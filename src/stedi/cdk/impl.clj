(ns stedi.cdk.impl
  (:refer-clojure :exclude [require])
  (:require [clojure.java.browse :as browse]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [stedi.cdk.jsii.client :as client])
  (:import (software.amazon.jsii JsiiObjectRef)))

(defn browse-docs [fqn]
  (let [sanitized (string/replace fqn "/" "_")]
    (browse/browse-url (format "https://docs.aws.amazon.com/cdk/api/latest/docs/%s.html"
                               sanitized))))

(defn type-name [fqn]
  (last (string/split fqn #"\.")))

(defn module-name [fqn]
  (first (string/split fqn #"\.")))

(declare wrap-objects unwrap-objects)

(defn doc-data [fqn]
  (-> fqn
      (module-name)
      (client/get-manifest)
      (get-in ["types" fqn])
      (walk/keywordize-keys)))

(defn invoke-object
  [cdk-object op & args]
  (assert (keyword? op) "op must be a keyword")
  (case op
    :cdk/browse (browse-docs (.getFqn (. cdk-object object-ref)))
    (-> (client/call-method (. cdk-object object-ref) (name op) (unwrap-objects args))
        (wrap-objects))))

(deftype CDKObject [object-ref]
  clojure.lang.ILookup
  (valAt [_ k]
    (case k
      :cdk/definition (doc-data (.getFqn object-ref))

      (-> (client/get-property-value object-ref (name k))
          (wrap-objects))))

  clojure.lang.IFn
  (invoke [this op]
    (invoke-object this op))
  (invoke [this op a]
    (invoke-object this op a))
  (invoke [this op a b]
    (invoke-object this op a b))
  (invoke [this op a b c]
    (invoke-object this op a b c)))

(defn wrap-objects
  [x]
  (walk/postwalk
    (fn [y]
      (if (= JsiiObjectRef (type y))
        (CDKObject. y)
        y))
    x))

(defn unwrap-objects
  [x]
  (walk/postwalk
    (fn [y]
      (if (= CDKObject (type y))
        (. y object-ref)
        y))
    x))

(defn invoke-class
  [cdk-class op & args]
  (assert (keyword? op) "op must be a keyword")
  (let [fqs       (. cdk-class fqs)
        fqn       (. cdk-class fqn)
        overrides (some-> fqs resolve meta ::overrides)]
    (case op
      :cdk/create (let [arg-count (-> cdk-class
                                      :cdk/definition
                                      :initializer
                                      :parameters
                                      count)
                        args*     (take arg-count args)
                        obj       (CDKObject. (client/create-object fqn (unwrap-objects args*)))]
                    (when-let [init-fn (:cdk/init overrides)]
                      (apply init-fn obj (rest args)))
                    obj)
      :cdk/browse (browse-docs fqn)

      (wrap-objects (client/call-static-method fqn (name op) (unwrap-objects args))))))

(deftype CDKClass [fqn fqs]
  clojure.lang.ILookup
  (valAt [_ k]
    (case k
      :cdk/definition (doc-data fqn)

      (-> (client/get-static-property-value fqn (name k))
          (wrap-objects))))

  clojure.lang.IFn
  (invoke [this op]
    (invoke-class this op))
  (invoke [this op a]
    (invoke-class this op a))
  (invoke [this op a b]
    (invoke-class this op a b))
  (invoke [this op a b c]
    (invoke-class this op a b c)))

(defn wrap-class [fqn fqs]
  (CDKClass. fqn fqs))

(defn make-rest-args-optional [x]
  (list* (update (into [] x) 1 conj '& '_)))

(defn package->ns-sym [package]
  (-> package
      (string/replace "@" "")
      (string/replace "/" ".")
      (symbol)))
