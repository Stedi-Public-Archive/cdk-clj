(ns stedi.cdk.main
  (:require [clojure.data.json :as json]
            [clojure.java.classpath :as cp]
            [clojure.tools.cli :as cli]
            [clojure.tools.namespace.find :as ctn.find]
            [stedi.cdk.alpha :as cdk]
            [stedi.jsii.alpha :as jsii]))

(cdk/import [[App] :from "@aws-cdk/core"])

(defn load-project-files
  []
  (->> (cp/classpath-directories)
       (ctn.find/find-namespaces)
       (map #(try (require %) % (catch Exception _)))
       (remove nil?)
       (doall)))

(defn find-app
  []
  (let [apps (->> (load-project-files)
                  (mapcat ns-publics)
                  (map second)
                  (filter (comp #(and (jsii/jsii-primitive? %)
                                      (App/isApp %))
                                deref)))]
    (assert (not-empty apps) "No app was found.")
    (assert (= 1 (count apps)) "More than one app was found.")
    (deref (first apps))))

(defn config
  [{:keys [aliases]}]
  (spit "cdk.json"
        (json/write-str
          {:app (str "clojure"
                     (when aliases
                       (str " -C:" aliases " -R:" aliases))
                     " -m stedi.cdk.main synth")}
          :escape-slash false)))

(defn synth
  [_]
  (let [app (find-app)]
    (App/synth app)))

(def actions
  {:config {:cli-options [[nil "--aliases ALIASES" "Colon seperated classpath aliases to use during synth"]]
            :handler     config}
   :synth  {:handler synth}})

(defn do-main
  [args]
  (let [action-kw         (keyword (first args))
        {:keys [cli-options
                handler]} (get actions action-kw)
        {:keys [options]} (cli/parse-opts args (or cli-options []))]
    (handler options)))

(defn -main [& args]
  (try
    (do-main args)
    (finally
      (shutdown-agents))))
