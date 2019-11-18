(ns stedi.jsii.alpha-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :as t :refer [deftest testing is]]
            [stedi.jsii.alpha :as jsii]))

(jsii/import-fqn "@aws-cdk/core.App" 'App)
(jsii/import-fqn "@aws-cdk/core.Stack" 'Stack)
(jsii/import-fqn "@aws-cdk/core.TagType" 'TagType)
(jsii/import-fqn "@aws-cdk/aws-s3.Bucket" 'Bucket)

(deftest jsii-class-test
  (let [C (jsii/get-class "@aws-cdk/core.Stack")]
    (is (instance? stedi.jsii.alpha.impl.JsiiClass C))

    (testing "invoking a class invokes the constructor"
      (let [stack (C)]
        (is (instance? stedi.jsii.alpha.impl.JsiiObject stack))))

    (testing "classes extend ILookup for static property access"
      (is (:ACCOUNT_ID (jsii/get-class "@aws-cdk/core.Aws"))))))

(deftest jsii-object-test
  (let [C (jsii/get-class "@aws-cdk/core.Stack")
        stack (C nil "my-stack")]
    (testing "objects extend ILookup for property access"
      (is (= "my-stack" (:stackName stack))))))

(deftest import-fqn-test
  (testing "refers a jsii primitive into the current namespace"
    (is (jsii/jsii-primitive? App)))

  (testing "creates a namespace for the specified jsii class"
    (is (find-ns 'aws-cdk.core.App)))

  (testing "aliases created namespace to the specified symbol"
    (is (= (find-ns 'aws-cdk.core.App)
           (-> (ns-aliases (find-ns 'stedi.jsii.alpha-test))
               (get 'App))))))

(deftest importing-a-class-test
  (testing "refers a jsii class into the current namespace"
    (is (instance? stedi.jsii.alpha.impl.JsiiClass App)))

  (testing "interns class functions into created namespace"
    (let [interned-symbols
          (->> (find-ns 'aws-cdk.core.App)
               (ns-map)
               (map second)
               (filter var?)
               (map symbol)
               (set))]
      (is (= '#{aws-cdk.core.App/synth
                aws-cdk.core.App/isApp
                aws-cdk.core.App/isConstruct
                aws-cdk.core.App/toString}
             interned-symbols))))

  (testing "interned functions are specced"
    (s/get-spec 'aws-cdk.core.App/synth))

  (testing "interned functions are instrumented"
    (is (.contains (str aws-cdk.core.App/synth)
                   "spec_checking_fn")))

  (testing "map specs are closed"
    (let [ex (is (thrown? Exception (Bucket (Stack) "bucket" {:does-not-exist "foo"})))]
      (is (:clojure.spec.alpha/problems (ex-data ex)))))

  (testing "interned static functions have the correct arglists"
    (is (= '([x])
           (-> (resolve 'aws-cdk.core.Stack/isStack)
               (meta)
               (:arglists)))))

  (testing "interned instance functions have the correct arglists"
    (is (= '([this stack] [this stack reason])
           (-> (resolve 'aws-cdk.core.Stack/addDependency)
               (meta)
               (:arglists))))))

(deftest importing-an-enum-test
  (testing "refers a jsii enum into the current namespace"
    (is (instance? stedi.jsii.alpha.impl.JsiiEnumClass TagType)))

  (testing "interns enum members into created namespace"
    (let [interned-symbols
          (->> (find-ns 'aws-cdk.core.TagType)
               (ns-map)
               (map second)
               (filter var?)
               (map symbol)
               (set))]
      (is (= '#{aws-cdk.core.TagType/STANDARD
                aws-cdk.core.TagType/AUTOSCALING_GROUP
                aws-cdk.core.TagType/MAP
                aws-cdk.core.TagType/NOT_TAGGABLE
                aws-cdk.core.TagType/KEY_VALUE}
             interned-symbols))))

  (testing "interned members are enum members"
    (is (instance? stedi.jsii.alpha.impl.JsiiEnumMember TagType/STANDARD))))

(comment
  (t/run-tests)

 )
