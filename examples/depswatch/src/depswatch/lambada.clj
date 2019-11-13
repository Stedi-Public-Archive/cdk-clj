(ns depswatch.lambada
  (:require [clojure.data.json :as json]
            [uswitch.lambada.core :refer [deflambdafn]]))

(deflambdafn depswatch.GetRepos
  [in out ctx]
  (.write out
          (.getBytes
            (json/write-str
              [{:owner "stediinc"
                :repo  "cdk-clj"}]))))

(deflambdafn depswatch.GetDepsEdn
  [in out ctx]
  (.write out
          (.getBytes
            (json/write-str
              (pr-str
                {:deps {'midje {:mvn/version "1.9.8"}}})))))

(deflambdafn depswatch.CheckNewer
  [in out ctx]
  (.write out
          (.getBytes
            (json/write-str
              (pr-str
                {'midje {:old-version "1.9.8"
                         :new-version "1.9.9"}})))))

(deflambdafn depswatch.CheckSent
  [in out ctx]
  (.write out
          (.getBytes
            (json/write-str
              false))))

(deflambdafn depswatch.NotifySlack
  [in out ctx]
  (.write out
          (.getBytes
            (json/write-str
              "Slack notified"))))
