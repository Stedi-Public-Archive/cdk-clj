(ns stedi.cdk.lambda.build-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is]]
            [stedi.cdk.lambda.build :as build]))

(defn- clean-test-dir
  []
  (doseq [file (reverse (file-seq (io/file "target/test")))]
    (io/delete-file file true))
  (io/make-parents "target/test/."))

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
                  (:src after)))))))
