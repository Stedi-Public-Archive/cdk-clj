(ns stedi.cdk.lambda
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.namespace.find :as ns-find]
            [mach.pack.alpha.aws-lambda :as lambda-pack]
            [mach.pack.alpha.impl.elodin :as elodin]
            [mach.pack.alpha.impl.lib-map :as lib-map]
            [mach.pack.alpha.impl.tools-deps :as tools-deps]
            [mach.pack.alpha.impl.vfs :as vfs]
            [stedi.cdk :as cdk])
  (:import (java.security MessageDigest)
           (java.util Base64)))

(defonce digest-alg (MessageDigest/getInstance "MD5"))

(defonce b64-encoder (Base64/getEncoder))

(defn calc-hash
  [s]
  (->> s
       (.getBytes)
       (.digest digest-alg)
       (BigInteger. 1)
       (format "%032x")))

;; TODO:
;; - allow aot layer to be parameterized

(defn write-layer-zip
  [lib-map output-dir]
  (let [hash     (calc-hash (pr-str lib-map))
        out-file (str output-dir hash ".zip")]
    (when-not (.exists (io/file out-file))
      (io/make-parents out-file)
      (vfs/write-vfs
        {:type   :zip
         :stream (io/output-stream out-file)}
        (concat
          (map
            (fn [{:keys [path] :as all}]
              {:input (io/input-stream path)
               :path  ["java/lib" (elodin/jar-name all)]})
            (lib-map/lib-jars lib-map))

          (map
            (fn [{:keys [path] :as all}]
              {:paths (vfs/files-path
                        (file-seq (io/file path))
                        (io/file path))
               :path  ["java/lib" (format "%s.jar" (elodin/directory-name all))]})
            (lib-map/lib-dirs lib-map)))))
    out-file))

(defn write-source-zip [paths output-dir]
  (let [hash     (->> (mapcat (comp file-seq io/file) paths)
                      (remove #(.isDirectory %))
                      (map slurp)
                      (apply str)
                      (calc-hash))
        out-file (str output-dir hash ".zip")]
    (when-not (.exists (io/file out-file))
      (io/make-parents out-file)
      (lambda-pack/write-zip
        {::tools-deps/paths paths}
        out-file))
    out-file))

(defn build-lib-layer
  [build-dir]
  (-> (tools-deps/slurp-deps nil)
      (select-keys [:deps])
      (update :deps merge '{com.amazonaws/aws-lambda-java-core {:mvn/version "1.2.0"}
                            org.clojure/data.json              {:mvn/version "0.2.6"}})
      (deps/resolve-deps {})
      (write-layer-zip (str build-dir "lib-layer-"))))

(defn build-aot-layer
  [build-dir]
  (let [paths      (:paths (tools-deps/slurp-deps nil))
        aot-dir    (str build-dir "aot/")
        layer-dir  (str aot-dir "classes/")
        path-files (map io/file paths)
        external-nses
        (->> path-files
             (mapcat ns-find/find-ns-decls-in-dir)
             (tree-seq seqable? seq)
             (filter #(and (list? %)
                           (= :require (first %))))
             (mapcat rest)
             (map first)
             (remove (set (mapcat ns-find/find-namespaces-in-dir path-files)))
             (set))]
    (io/make-parents (io/file (str layer-dir ".")))
    (binding [*compile-path* layer-dir]
      (doseq [ns (conj external-nses 'stedi.cdk.lambda.handler)]
        (compile ns)))
    (spit (str aot-dir "deps.edn") "{:paths [\"classes\"] :deps {}}")
    (-> {:deps `{~'cdk.app/aot-layer {:local/root ~aot-dir}}}
        (deps/resolve-deps {})
        (select-keys ['cdk.app/aot-layer])
        (write-layer-zip (str build-dir "aot-layer-")))))

(defn build-src-layer
  [build-dir]
  (-> (tools-deps/slurp-deps nil)
      (:paths)
      (write-source-zip (str build-dir "src-"))))

(defn build-layers
  [build-dir]
  (let [build-dir*     (str build-dir
                            (when-not (re-find #"/$" build-dir) "/"))]
    {:lib-layer (build-lib-layer build-dir*)
     :aot-layer (build-aot-layer build-dir*)
     :src       (build-src-layer build-dir*)}))

(cdk/require ["@aws-cdk/core" cdk-core]
             ["@aws-cdk/aws-lambda" lambda])

(cdk/defextension clj-lambda cdk-core/Construct
  :cdk/init
  (fn [this _name {:keys [fn environment]}]
    (let [path      (get-in this [:node :path])
          build-dir (str "./target/" (string/replace path "/" "_"))
          {:keys [lib-layer aot-layer src]}
          (build-layers build-dir)]
      (lambda/Function :cdk/create this "function"
                       {:code        (lambda/Code :cdk/asset src)
                        :handler     "stedi.cdk.lambda.handler::handler"
                        :runtime     (:JAVA_8 lambda/Runtime)
                        :environment (merge {"STEDI_LAMBDA_ENTRYPOINT" (str (symbol fn))}
                                            environment)
                        :memorySize  2048
                        :layers
                        [(lambda/LayerVersion :cdk/create this "lib-layer"
                                              {:code (lambda/Code :cdk/asset lib-layer)})
                         (lambda/LayerVersion :cdk/create this "class-layer"
                                              {:code (lambda/Code :cdk/asset aot-layer)})]}))))
