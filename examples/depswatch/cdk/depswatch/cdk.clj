(ns depswatch.cdk
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [stedi.cdk :as cdk]
            [uberdeps.api :as uberdeps]))

(cdk/import ["@aws-cdk/core"
             App
             Construct
             Duration
             Stack]
            ["@aws-cdk/aws-apigateway"
             LambdaRestApi]
            ["@aws-cdk/aws-dynamodb"
             AttributeType
             Table]
            ["@aws-cdk/aws-events"
             Rule
             Schedule]
            ["@aws-cdk/aws-events-targets"
             SfnStateMachine]
            ["@aws-cdk/aws-lambda"
             Code
             Function
             Runtime
             Tracing]
            ["@aws-cdk/aws-stepfunctions"
             Chain
             Choice
             Condition
             Data
             Map
             StateMachine
             Succeed
             Task]
            ["@aws-cdk/aws-stepfunctions-tasks"
             InvokeFunction
             StartExecution])

(defn- clean
  []
  (->> (io/file "classes")
       (file-seq)
       (reverse)
       (map io/delete-file)
       (dorun)))

(def code
  (let [jarpath "target/app.jar"
        deps    (edn/read-string (slurp "deps.edn"))]
    (with-out-str
      (clean)
      (io/make-parents "classes/.")
      (io/make-parents jarpath)
      (compile 'depswatch.lambada)
      (uberdeps/package deps jarpath {:aliases [:classes]}))
    (Code/fromAsset jarpath)))

(defn LambadaFunction
  [scope id props]
  (Function scope
            id
            (merge {:code       code
                    :memorySize 2048
                    :runtime    (:JAVA_8 Runtime)
                    :tracing    Tracing/ACTIVE}
                   props)))

(defn TaskFunction
  [scope id {:keys [handler resultPath]}]
  (let [construct (Construct scope id)
        fn        (LambadaFunction construct "fn" {:handler handler})
        task      (Task construct
                        id
                        {:task       (InvokeFunction fn)
                         :resultPath (or resultPath "$")})]
    task))

(defn RunJobStateMachine
  [scope id]
  (let [construct (Construct scope id)
        definition
        (-> (Chain/start
              (TaskFunction construct
                            "get-deps-edn"
                            {:handler    "depswatch.GetDepsEdn"
                             :resultPath "$.depsEdn"}))
            (Chain/next
              (TaskFunction construct
                            "check-newer"
                            {:handler    "depswatch.CheckNewer"
                             :resultPath "$.newer"}))
            (Chain/next
              (TaskFunction construct
                            "check-sent"
                            {:handler    "depswatch.CheckSent"
                             :resultPath "$.sent"}))
            (Chain/next
              (doto (Choice construct "was-sent")
                (Choice/when (Condition/booleanEquals "$.sent" true)
                  (Succeed construct "already-sent"))

                (Choice/when (Condition/booleanEquals "$.sent" false)
                  (TaskFunction construct
                                "notify-slack"
                                {:handler "depswatch.NotifySlack"})))))]
    (StateMachine construct "sfn" {:definition definition})))

(defn StartJobsStateMachine
  [scope id {:keys [run-job]}]
  (let [construct (Construct scope id)
        task      (StartExecution run-job
                                  {:input {:owner (Data/stringAt "$.owner")
                                           :repo  (Data/stringAt "$.repo")}})
        definition
        (doto (TaskFunction construct
                            "get-repos"
                            {:handler "depswatch.GetRepos"})
          (Task/next
            (doto (Map construct "run-jobs")
              (Map/iterator
                (Task construct "run-job" {:task task})))))]
    (StateMachine construct "sfn" {:definition definition})))

(defn AppStack
  [scope id {:keys [timer?]}]
  (let [stack         (Stack scope id)
        watched-repos (Table stack
                             "watched-repos"
                             {:partitionKey {:name "owner"
                                             :type AttributeType/STRING}})
        slack-hook    (LambadaFunction stack
                                       "slack-hook"
                                       {:handler "depswatch.SlackHook"
                                        :environment
                                        {"WATCHED_REPOS" (:tableName watched-repos)}})
        run-job       (RunJobStateMachine stack "run-job")
        start-jobs    (StartJobsStateMachine stack "start-jobs" {:run-job run-job})]
    (when timer?
      (Rule stack
            "timer"
            {:schedule (Schedule/rate
                         (Duration/minutes 1))
             :targets  [(SfnStateMachine start-jobs)]}))
    (Table/grantReadWriteData watched-repos slack-hook)
    (LambdaRestApi slack-hook "api" {:handler slack-hook})
    stack))

(def app (App))

(AppStack app "depswatch-dev" {:timer? false})
(AppStack app "depswatch-prod" {:timer? true})
