(ns stedi.cdk.lambda
  (:require [clojure.java.io :as io]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.namespace.find :as ns-find]))

(defn build-lib-layer
  [build-dir deps]
  (let [layer-dir  (str build-dir "lib-layer/")
        target-dir (str layer-dir "java/lib/")
        lib-map
        (deps/resolve-deps
          {:deps      (merge '{com.amazonaws/aws-lambda-java-core {:mvn/version "1.2.0"}}
                             deps)
           :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                       "clojars" {:url "https://repo.clojars.org/"}}}
          {})]
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
  [build-dir paths]
  (let [layer-dir  (str build-dir "class-layer/")
        target-dir (str layer-dir "java/")
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
    (io/make-parents (io/file (str target-dir ".")))
    (binding [*compile-path* target-dir]
      (doseq [ns (conj external-nses 'stedi.cdk.lambda.handler)]
        (compile ns)))
    layer-dir))

(defn build
  [{:keys [deps paths build-dir]}]
  (let [build-dir* (str build-dir
                        (when-not (re-find #"/$" build-dir) "/"))]
    (doseq [file (reverse (file-seq (io/file build-dir*)))]
      (io/delete-file file))
    {:lib-layer-dir   (build-lib-layer build-dir* deps)
     :class-layer-dir (build-class-layer build-dir* paths)}))

(comment
  (build {:build-dir "./target/foo/"
          :deps      '{clj-http {:mvn/version "3.10.0"}}
          :paths     ["src"]})

  (file-seq (io/file "./target/foo/"))

  (build-class-layer ["src"])
  )
