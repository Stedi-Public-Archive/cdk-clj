(ns stedi.cdk.lambda.build
  (:require [clojure.java.io :as io]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as deps.reader]
            [mach.pack.alpha.impl.elodin :as elodin]
            [mach.pack.alpha.impl.lib-map :as lib-map]
            [mach.pack.alpha.impl.vfs :as vfs])
  (:import (java.security MessageDigest)))

(defonce digest-alg (MessageDigest/getInstance "MD5"))

(defn- calc-hash
  [s]
  (->> s
       (.getBytes)
       (.digest digest-alg)
       (BigInteger. 1)
       (format "%032x")))

(defn- slurp-deps
  []
  (deps.reader/slurp-deps "deps.edn"))

(defn- file-exists?
  [path]
  (.exists (io/file path)))

(defn- write-zip
  [paths out-file]
  (when-not (file-exists? out-file)
    (io/make-parents out-file)
    (vfs/write-vfs
      {:type   :zip
       :stream (io/output-stream out-file)}
      paths))
  out-file)

(defn- lib-jars
  [lib-map]
  (map
    (fn [{:keys [path] :as all}]
      {:input (io/input-stream path)
       :path  ["java/lib" (elodin/jar-name all)]})
    (lib-map/lib-jars lib-map)))

(defn- lib-dirs
  [lib-map]
  (map
    (fn [{:keys [path] :as all}]
      {:paths (vfs/files-path
                (file-seq (io/file path))
                (io/file path))
       :path  ["java/lib" (format "%s.jar" (elodin/directory-name all))]})
    (lib-map/lib-dirs lib-map)))

(defn- src-paths
  [paths]
  (mapcat
    (fn [dir]
      (let [root (io/file dir)]
        (vfs/files-path
          (file-seq root)
          root)))
    paths))

(defn- write-layer-zip
  [lib-map output-path-prefix]
  (let [paths    (concat (lib-jars lib-map)
                         (lib-dirs lib-map))
        hash     (->> paths
                      (mapv :path)
                      (pr-str)
                      (calc-hash)) 
        out-file (str output-path-prefix hash ".zip")]
    (write-zip paths out-file)))

(defn- write-src-zip
  [paths output-path-prefix]
  (let [src-paths* (src-paths (butlast paths))
        ;; This hash needs to include the aot settings
        hash       (->> src-paths*
                        (mapv (juxt :path :last-modified))
                        (pr-str)
                        (calc-hash))
        out-file   (str output-path-prefix hash ".zip")]
    (write-zip src-paths* out-file)))

(defn- clean-dir
  [dir]
  (doseq [f (reverse (file-seq (io/file dir)))]
    (io/delete-file f true)))

(def ^:private default-repos
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org/"}})

(def ^:private lambda-entrypoint-deps
  {'stedi/cdk-lambda {:git/url   "git@github.com:stediinc/cdk-kit.git"
                      :deps/root "lambda"
                      :sha       "9466e86d88369eac43256c93d77d61814e035d5a"}})

(defn- build-lib-layer ;; should hash here and get rid of write-layer-zip
  [deps-map build-dir]
  (-> deps-map
      (select-keys [:deps])
      (update :deps (partial merge lambda-entrypoint-deps))
      (update :mvn/repos (partial merge default-repos))
      (deps/resolve-deps {})
      (write-layer-zip (str build-dir "lib-layer-"))))

(defn- build-src ;; should hash here and get rid of write-src-zip
  [deps-map build-dir aot]
  (let [paths       (:paths deps-map)
        classes-dir (str build-dir "classes/")
        aot-nses    (concat ['stedi.lambda.entrypoint] aot)]
    (clean-dir classes-dir)
    (io/make-parents (io/file (str classes-dir ".")))
    (binding [*compile-path* classes-dir]
      (doseq [ns aot-nses]
        (compile ns)))
    (write-src-zip (concat paths [classes-dir])
                   (str build-dir "src-"))))

(defn build
  [aot]
  (let [deps-map  (slurp-deps)
        build-dir "./target/cdk-artifacts/"]
    {:lib-layer (build-lib-layer deps-map build-dir)
     :src       (build-src deps-map build-dir aot)}))

(comment
  (build-layers [])
  )
