(ns stedi.cdk.lambda
  (:require [clojure.java.io :as io]
            [clojure.tools.deps.alpha :as deps]))

(defn build-lib-layer
  [build-dir deps]
  (let [layer-dir  (str build-dir "lib-layer/")
        target-dir (str build-dir "java/lib/")
        lib-map
        (deps/resolve-deps
          {:deps      deps
           :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                       "clojars" {:url "https://repo.clojars.org/"}}}
          {:extra-deps '{stedi/lambda-kit
                         {:git/url "git@github.com:StediInc/lambda-kit.git"
                          :sha     "d2f0bc2c87beab7efff2f78d642699077ea52116"}}})]
    (transduce (comp (mapcat (comp :paths second))
                     (filter #(re-find #"\.jar$" %))
                     (map io/file))
               (completing
                 (fn [_ src-file]
                   (let [dest      (str target-dir (.getName src-file))
                         dest-file (io/file dest)]
                     (io/make-parents dest-file)
                     (io/copy src-file dest-file))))
               nil
               lib-map)
    layer-dir))

(defn build-class-layer
  [])

(defn build-src-layer
  [])

(defn build
  [{:keys [deps paths build-dir]}]
  (let [build-dir* (str build-dir
                        (when-not (re-find #"/$" build-dir) "/"))]
    (doseq [file (reverse (file-seq (io/file build-dir*)))]
      (io/delete-file file))
    {:lib-layer-dir (build-lib-layer build-dir* deps)}))

(comment
  (build {:build-dir "./target/foo/"
          :deps      '{}})

  (file-seq (io/file "./target/foo/"))

  
  )
