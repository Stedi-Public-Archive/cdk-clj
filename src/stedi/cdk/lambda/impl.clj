(ns stedi.cdk.lambda.impl
  (:require [clojure.java.io :as io]
            [clojure.tools.deps.alpha :as deps]
            [mach.pack.alpha.aws-lambda :as lambda-pack]
            [mach.pack.alpha.impl.elodin :as elodin]
            [mach.pack.alpha.impl.lib-map :as lib-map]
            [mach.pack.alpha.impl.tools-deps :as tools-deps]
            [mach.pack.alpha.impl.vfs :as vfs])
  (:import (java.security MessageDigest)
           (java.util Base64)))

(defonce digest-alg (MessageDigest/getInstance "MD5"))

(defn calc-hash
  [s]
  (->> s
       (.getBytes)
       (.digest digest-alg)
       (BigInteger. 1)
       (format "%032x")))

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
                      (filter #(.exists %))
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
      (update :mvn/repos merge {"central" {:url "https://repo1.maven.org/maven2/"}
                                "clojars" {:url "https://repo.clojars.org/"}})
      (deps/resolve-deps {})
      (write-layer-zip (str build-dir "lib-layer-"))))

(defn build-aot-layer
  [build-dir aot]
  (let [paths     (:paths (tools-deps/slurp-deps nil))
        aot-dir   (str build-dir "aot/")
        layer-dir (str aot-dir "classes/")
        aot-nses  (concat ['stedi.cdk.lambda.handler] aot)]
    (doseq [f (reverse (file-seq (io/file aot-dir)))]
      (io/delete-file f true))
    (io/make-parents (io/file (str layer-dir ".")))
    (binding [*compile-path* layer-dir]
      (doseq [ns aot-nses]
        (compile ns)))
    (spit (str aot-dir "deps.edn") "{:paths [\"classes\"] :deps {}}")
    (-> {:deps `{~'cdk.app/aot-layer {:local/root ~aot-dir}}}
        (deps/resolve-deps {})
        (select-keys ['cdk.app/aot-layer])
        (assoc-in ['cdk.app/aot-layer :cdk/aot-nses] aot-nses)
        (write-layer-zip (str build-dir "aot-layer-")))))

(defn build-src-layer
  [build-dir]
  (-> (tools-deps/slurp-deps nil)
      (:paths)
      (write-source-zip (str build-dir "src-"))))

(defn build-layers
  [build-dir aot]
  (let [build-dir*     (str build-dir
                            (when-not (re-find #"/$" build-dir) "/"))]
    {:lib-layer (build-lib-layer build-dir*)
     :aot-layer (build-aot-layer build-dir* aot)
     :src       (build-src-layer build-dir*)}))
