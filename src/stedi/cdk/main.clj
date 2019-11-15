(ns stedi.cdk.main
  (:require [stedi.cdk :as cdk]))

(cdk/import [[App] :from "@aws-cdk/core"])

(defn -main [& [app-sym]]
  (let [app @(requiring-resolve (symbol app-sym))]
    (App/synth app))
  (shutdown-agents)
  (System/exit 0))
