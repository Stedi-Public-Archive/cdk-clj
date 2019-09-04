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
  (applyTo [this arg-list]
    (apply invoke-object this arg-list))
  (invoke [this op]
    (invoke-object this op))
  (invoke [this op a]
    (invoke-object this op a))
  (invoke [this op a b]
    (invoke-object this op a b))
  (invoke [this op a b c]
    (invoke-object this op a b c))

  clojure.lang.Seqable
  (seq [this]
    (seq
      (into {} (comp (remove :static)
                     (map (comp keyword :name))
                     (map #(vector % (% this))))
            (:properties (doc-data (.getFqn object-ref))))))

  clojure.lang.IPersistentCollection
  (cons [this x] this)
  (empty [this] this)

  java.lang.Object
  (toString [this]
    (try
      (invoke-object this :toString)
      (catch Exception _
        (.getFqn object-ref)))))

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

(defn create-object [cdk-class overrides args]
  (let [fqn            (. cdk-class fqn)
        obj-ref        (atom nil)
        constructor-fn (fn [& args*]
                         (if-not @obj-ref
                           (let [obj (CDKObject. (client/create-object fqn (unwrap-objects args*)))]
                             (reset! obj-ref obj))
                           (throw (Exception. "constructor-fn can only be called once"))))]
    (if-let [build-fn (:cdk/build overrides)]
      (apply build-fn constructor-fn args)
      (apply constructor-fn args))
    (if-let [obj @obj-ref]
      (when-let [init-fn (:cdk/init overrides)]
        (apply init-fn obj (rest args)))
      (throw (Exception. "constructor-fn wasn't called in :cdk/build")))
    @obj-ref))

(defn invoke-class
  [cdk-class op & args]
  (if (keyword? op)
    (let [fqs       (. cdk-class fqs)
          fqn       (. cdk-class fqn)
          overrides (some-> fqs resolve meta ::overrides)]
      (case op
        :cdk/create (create-object cdk-class overrides args)
        :cdk/browse (browse-docs fqn)
        :cdk/enum   {"$jsii.enum" (str fqn "/" (name (first args)))}

        (wrap-objects (client/call-static-method fqn (name op) (unwrap-objects args)))))
    (create-object cdk-class {} (concat [op] args))))

(deftype CDKClass [fqn fqs]
  clojure.lang.ILookup
  (valAt [_ k]
    (case k
      :cdk/definition (doc-data fqn)

      (-> (client/get-static-property-value fqn (name k))
          (wrap-objects))))

  clojure.lang.Seqable
  (seq [this]
    (seq
      (into {} (comp (filter :static)
                     (map (comp keyword :name))
                     (map #(vector % (% this))))
            (:properties (doc-data fqn)))))

  clojure.lang.IFn
  (applyTo [this arg-list]
    (apply invoke-class arg-list))
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
