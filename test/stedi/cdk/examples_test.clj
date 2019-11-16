(ns stedi.cdk.examples-test
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [clojure.test :refer [deftest testing is]]))

(def cdk-bin-path "node_modules/aws-cdk/bin/cdk")

(defn example-projects
  []
  (->> (file-seq (io/file "examples"))
     (map #(.split (str %) "/"))
     (filter #(= 2 (count %)))
     (map second)))

(defn synth
  [project]
  (let [{:keys [exit out err]}
        (sh/with-sh-dir (format "examples/%s" project)
          (sh/sh (str "../../" cdk-bin-path) "synth"))]
    (when-not (= 0 exit)
      (println (format "===== [%s] failed to synth! =====" project))
      (when-not (string/blank? out)
        (println)
        (println "Out:")
        (println)
        (println out))
      (when-not (string/blank? err)
        (println)
        (println "Err:")
        (println)
        (println err))
      (throw (Exception. (str "An error occured running cdk synth. "
                              "Check logs for more info."))))
    true))

(deftest ^:integration synth-examples-test
  (assert (.exists (io/file cdk-bin-path)) "Could not find cdk executable. Did you run `npm install`?")
  (doseq [project (example-projects)]
    (testing (str "cdk synth " project)
      (is (synth project)))))
