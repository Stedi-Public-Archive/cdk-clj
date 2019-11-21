(ns stedi.basics.cdk
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [stedi.cdk.alpha :as cdk]
            [uberdeps.api :as uberdeps]
            [clojure.datafy :as d]))

;; cdk-clj wraps the JSii protocol for CDK classes in Clojure.

;; The best way of getting information about what is availble via CDK
;; is to call `(cdk/browse)` in the REPL. This will take you to the
;; AWS CDK API reference documentation.

(comment
  (cdk/browse)
  )

;; CDK applications consist of Constructs arranged into a tree
;; structure. Constructs represent one or more AWS resources. All CDK
;; applications have an App construct at the root of the
;; tree. `cdk-clj` exposes access to these constructs through the
;; `cdk/import` macro.

(cdk/import [[App] :from "@aws-cdk/core"])

;; Import does two things:
;; 1. makes App resolvable to a jsii-class in the local namespace
;; 2. aliases the ns for "@aws-cdk/core.App" to App

;; App will now resolve to a jsii-class
App

;; Invoking the class calls its constructor and returns a
;; jsii-instance:
(def app (App {:outdir "cdk.out"}))

;; These constructor vars also have docstrings
App

;; Import also makes an alias to the ns that contains all the static
;; and instance methods for App
App/isApp
App/synth

;; You can also browse to a constructs documentation via browse on
;; the constructor or an instance:

(comment
  (cdk/browse app)
  (cdk/browse App)
  )

;; Applications are composed of one or more Stacks, each representing
;; a CloudFormation Stack. A Stack is a Construct as well.

(cdk/import [[Stack] :from "@aws-cdk/core"])

;; Child constructs are connected to their parent by passing in the
;; parent as the scope of the child's constructor function.

(def stack (Stack app "cdk-clj-basics"))

;; Class instances implement the ILookup interface so they work with
;; keyword lookups

(:stackName stack)

;; A stack needs at least one resource Construct in order to be
;; deployable so lets add a bucket.

(cdk/import [[Bucket] :from "@aws-cdk/aws-s3"])

;; cdk-clj generates specs for and instruments all jsii constructors
;; and functions:

(comment
  (Bucket stack nil) ; Fails with spec error
  )

(def bucket (Bucket stack "bucket"))

;; Buckets aren't particularly interesting, and lambdas + serverless
;; are all the rage so lets add a lambda function as well.

(cdk/import [[Duration] :from "@aws-cdk/core"]
            [[Code Function Runtime] :from "@aws-cdk/aws-lambda"])

(defn- clean
  []
  (->> (io/file "classes")
       (file-seq)
       (reverse)
       (map io/delete-file)
       (dorun)))

(def jarpath "target/app.jar")

;; Build an uberjar with the compiled source + dependency classes

(let [deps (edn/read-string (slurp "deps.edn"))]
  (when (.exists (io/file "classes")) (clean))
  (with-out-str
    (io/make-parents "classes/.")
    (io/make-parents jarpath)
    (compile 'stedi.lambada)
    (uberdeps/package deps jarpath {:aliases [:classes]})))

(comment
  (cdk/browse Function)
  )

(def my-fn
  (Function stack
            "my-fn"
            {:code        (Code/fromAsset jarpath)        ;; Calling a static method
             :handler     "stedi.cdk.basics.Hello"
             :runtime     (:JAVA_8 Runtime)               ;; Getting a static property
             :environment {"BUCKET" (:bucketName bucket)} ;; Getting an instance property
             :timeout     (Duration/seconds 5)
             :memorySize  2000
             }))

;; We can grant the function write access to the bucket using an
;; instance method
(Bucket/grantReadWrite bucket my-fn)

;; Synthesize our application into the cdk.out directory for use with
;; the CLI
(App/synth app)
















(comment
  ;; Demo Notes
  (do
    (cdk/browse) ;; root
    (clojure.java.browse/browse-url "https://docs.aws.amazon.com/cdk/api/latest/docs/aws-construct-library.html") ;; constructs
    (clojure.java.browse/browse-url "https://docs.aws.amazon.com/cdk/latest/guide/home.html") ;; developer guide
    (cdk/browse app) ;; for app documentation
    (clojure.java.browse/browse-url "https://console.aws.amazon.com/cloudformation/home?region=us-east-1#/stacks?filteringText=&filteringStatus=active&viewNested=true&hideStacks=false")
    )

  ;; synth basics:
  ;; cdk --app cdk.out synth
  ;; cdk --app cdk.out --profile tvanhens deploy
  ;; aws --profile tvanhens lambda invoke --function-name  /dev/stdout

  ;; synth depswatch:
  ;; cdk --app cdk.out synth -e depswatch-prod
  )
