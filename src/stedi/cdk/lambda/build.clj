(ns stedi.cdk.lambda.build
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as deps.reader]
            [mach.pack.alpha.impl.elodin :as elodin]
            [mach.pack.alpha.impl.lib-map :as lib-map]
            [mach.pack.alpha.impl.vfs :as vfs])
  (:import (java.nio.file Files)
           (java.security MessageDigest)))

(defonce digest-alg (MessageDigest/getInstance "MD5"))

(defn- hash-bytes
  [b]
  (->> b
       (.digest digest-alg)
       (BigInteger. 1)
       (format "%032x")))

(defn- hash-string
  [s]
  (hash-bytes (.getBytes s)))

(defn- slurp-deps
  []
  (deps.reader/slurp-deps "deps.edn"))

(defn- file-exists?
  [path]
  (.exists (io/file path)))

(defn- write-zip
  [paths out-file]
  (io/make-parents out-file)
  (vfs/write-vfs
    {:type   :zip
     :stream (io/output-stream out-file)}
    paths)
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

(defn- clean-dir
  [dir]
  (doseq [f (reverse (file-seq (io/file dir)))]
    (io/delete-file f true)))

(def ^:private default-repos
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org/"}})

(def ^:private lambda-entrypoint-deps
  {'stedi/cdk-lambda {:git/url   "git@github.com:stediinc/cdk-clj.git"
                      :deps/root "lambda"
                      :sha       "9466e86d88369eac43256c93d77d61814e035d5a"}})

(defn- build-lib-layer
  [deps-map build-dir]
  (let [deps-map* (-> deps-map
                      (select-keys [:deps])
                      (update :deps (partial merge lambda-entrypoint-deps))
                      (update :mvn/repos (partial merge default-repos)))
        hash      (-> deps-map* (:deps) (pr-str) (hash-string))
        out-file  (str build-dir "lib-layer-" hash ".zip")]
    (when-not (file-exists? out-file)
      (let [lib-map (deps/resolve-deps deps-map* {})
            paths   (concat (lib-jars lib-map)
                            (lib-dirs lib-map))]
        (write-zip paths out-file)))
    out-file))

(defn- paths-hash
  [paths aot]
  (->> (mapcat (comp file-seq io/file) paths)
       (filter #(.exists %))
       (remove #(.isDirectory %))
       (map (juxt #(.getPath %)
                  (comp #(hash-bytes %)
                        #(Files/readAllBytes %)
                        #(.toPath %))))
       (map #(str (first %) "@" (second %)))
       (sort)
       (cons (pr-str aot))
       (string/join "\n")
       (hash-string)))

(defn- build-src
  [deps-map build-dir aot]
  (let [paths    (-> deps-map
                     (:paths)
                     (src-paths))
        hash     (paths-hash (:paths deps-map) aot)
        out-file (str build-dir "src-" hash ".zip")]
    (when-not (file-exists? out-file)
      (let [classes-dir (str build-dir "classes/")
            aot-nses    (concat ['stedi.lambda.entrypoint] aot)]
        (clean-dir classes-dir)
        (io/make-parents (io/file (str classes-dir ".")))
        (binding [*compile-path* classes-dir]
          (doseq [ns aot-nses]
            (compile ns)))
        (write-zip (concat paths (src-paths [classes-dir])) out-file)))
    out-file))

(defn build
  ([aot] (build aot (slurp-deps)))
  ([aot deps-map]
   (let [build-dir "./target/cdk-artifacts/"]
     {:lib-layer (build-lib-layer deps-map build-dir)
      :src       (build-src deps-map build-dir aot)})))
