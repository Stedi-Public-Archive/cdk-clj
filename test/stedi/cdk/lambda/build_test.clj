(ns stedi.cdk.lambda.build-test
  (:require [clojure.java.io :as io]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.tools.deps.alpha :as deps]
            [stedi.cdk.lambda.build :as build]))

(defn- clean-test-dir
  []
  (doseq [file (reverse (file-seq (io/file "target/test")))]
    (io/delete-file file true))
  (io/make-parents "target/test/."))

(defn- clean-artifact-dir
  []
  (doseq [file (reverse (file-seq (io/file "target/cdk-artifacts")))]
    (io/delete-file file true)))

(deftest build-test
  (testing "deps are cached in lib-layer"
    (let [deps-map {:deps {'org.clojure/clojure {:mvn/version "1.10.0"}}}
          before   (build/build [] deps-map)
          after    (build/build [] deps-map)]
      (is (= (:lib-layer before)
             (:lib-layer after)))))

  (testing "changes within :deps invalidate lib-layer cache"
    (let [deps-map         {:deps {'org.clojure/clojure {:mvn/version "1.10.0"}}}
          before           (build/build [] deps-map)
          updated-deps-map (assoc-in deps-map [:deps 'org.clojure/clojure :mvn/version] "1.9.0")
          after            (build/build [] updated-deps-map)]
      (is (not= (:lib-layer before)
                (:lib-layer after)))))

  (testing "changes outside of :deps don't affect the lib-layer cache"
    (let [deps-map         {:deps {'org.clojure/clojure {:mvn/version "1.10.0"}}}
          before           (build/build [] deps-map)
          updated-deps-map (assoc-in deps-map [:paths] ["src"])
          after            (build/build [] updated-deps-map)]
      (is (= (:lib-layer before)
             (:lib-layer after)))))

  (testing "files in :paths are cached in src"
    (clean-test-dir)
    (spit "target/test/test.edn" (pr-str {:hello "world"}))
    (let [deps-map {:paths ["target/test"]}
          before   (build/build [] deps-map)
          after    (build/build [] deps-map)]
      (is (= (:src-layer before)
             (:src-layer after)))))

  (testing "changes to files in :paths invalidate src cache"
    (clean-test-dir)
    (spit "target/test/test.edn" (pr-str {:hello "world"}))
    (let [deps-map {:paths ["target/test"]}
          before   (build/build [] deps-map)]
      (spit "target/test/test.edn" (pr-str {:stuff "things"}))
      (let [after (build/build [] deps-map)]
        (is (not= (:src before)
                  (:src after))))))

  (testing "new files in :paths invalidate src cache"
    (clean-test-dir)
    (spit "target/test/test.edn" (pr-str {:hello "world"}))
    (let [deps-map {:paths ["target/test"]}
          before   (build/build [] deps-map)]
      (spit "target/test/test2.edn" (pr-str {:hello "world"}))
      (let [after (build/build [] deps-map)]
        (is (not= (:src before)
                  (:src after))))))

  (testing "deleted files in :paths invalidate src cache"
    (clean-test-dir)
    (spit "target/test/test.edn" (pr-str {:hello "world"}))
    (let [deps-map {:paths ["target/test"]}
          before   (build/build [] deps-map)]
      (io/delete-file "target/test/test.edn")
      (let [after (build/build [] deps-map)]
        (is (not= (:src before)
                  (:src after))))))

  (testing "changing aot args invalidates src cache"
    (clean-test-dir)
    (spit "target/test/test.edn" (pr-str {:hello "world"}))
    (let [deps-map {:paths ["target/test"]}
          before   (build/build [] deps-map)]
      (let [after (build/build ['clojure.core] deps-map)]
        (is (not= (:src before)
                  (:src after))))))

  (testing "changes to dependencies invalidates src cache"
    (clean-test-dir)
    (spit "target/test/test.edn" (pr-str {:hello "world"}))
    (let [deps-map {:paths ["target/test"]
                    :deps '{org.clojure/clojure {:mvn/version "1.10.1"}}}
          before   (build/build [] deps-map)]
      (let [after (build/build [] (assoc-in deps-map [:deps 'org.clojure/clojure :mvn/version] "1.9.0"))]
        (is (not= (:src before)
                  (:src after))))))

  (testing "paths which don't exist don't break the build"
    (is (build/build [] {:paths ["foobar"]})))

  (testing "project mvn repos are honoured"
    (let [deps (volatile! nil)]
      (clean-artifact-dir)
      (with-redefs [deps/resolve-deps
                    (fn [deps-map _]
                      (vreset! deps deps-map)
                      {})]
        (build/build [] {:paths ["target/test"] :mvn/repos {"a-repo" {:url "an-url"}}})
        (is (= {:url "an-url"}
               (-> @deps :mvn/repos (get "a-repo"))))))))

(comment
  (t/run-tests)

  )
