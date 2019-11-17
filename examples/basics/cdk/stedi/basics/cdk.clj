(ns stedi.basics.cdk
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [stedi.cdk.alpha :as cdk]
            [uberdeps.api :as uberdeps]))

;; CDK is a framework released by Amazon that allows developers to
;; deploy CloudFormation-based infrastructure using their prefered
;; language.

;; It is built from TypeScript and made available to other languages
;; through the JSii protocol by Amazon. JSii allows other languages to
;; interact with JavaScript classes through an RPC protocol.

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
(def app (App))

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

  ;; Worth noting that the CDK specs are closed

  (Bucket stack "id" {:does-not-exist :foo}) ; Fails due to specs being closed
  )

(def bucket (Bucket stack "bucket"))

;; Buckets aren't particularly interesting, and lambdas + serverless
;; are all the rage so lets add a lambda function as well.

(cdk/import [[Code Function Runtime] :from "@aws-cdk/aws-lambda"])

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
             }))

(comment
  ;; See it bound to the construct tree
  (map (comp :path :node)
       (get-in stack [:node :children]))
  )

;; We can grant the function write access to the bucket using an
;; instance method

(Bucket/grantWrite bucket my-fn)

;; CDK constructs often have functions for granting permissions,
;; adding metrics and triggering events.

;; This app can now be deployed using `cdk-cli` in a shell with AWS
;; credentials configured.

;; Synth:
;; cdk synth

;; Deploy:
;; cdk deploy

;; Destroy:
;; cdk destroy
