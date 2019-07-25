(ns stedi.cdk.main)

(defn -main [& [app-sym]]
  (let [app (requiring-resolve (symbol app-sym))]
    (app :synth)))
