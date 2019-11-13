(ns stedi.jsii.impl
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [stedi.jsii.assembly :as assm]
            [stedi.jsii.client :as client]
            [stedi.jsii.fqn :as fqn])
  (:import (software.amazon.jsii JsiiObjectRef)))

(def ^:private valid-tokens
  #{"$jsii.byref"
    "$jsii.enum"
    "$jsii.date"
    "$jsii.map"})

(defn- jsii-type
  [x]
  (and (map? x)
       (some valid-tokens (keys x))))

;; This is to encompass conversions of different JSON-serialized jsii
;; types. Only $jsii.byref is currently implemented because we have
;; not seen examples of the other serializations yet. However, the
;; link below implies that we may run into additional types that need
;; to be implemented.
;; https://github.com/aws/jsii/blob/master/packages/jsii-java-runtime/project/src/main/java/software/amazon/jsii/JsiiObjectMapper.java
(defmulti ^:private ->type jsii-type)

(defn- ->clj
  [m]
  (walk/postwalk
    (fn [x]
      (if (jsii-type x)
        (->type x)
        x))
    m))

(defprotocol Invocable
  (-invoke [_ req]))

(defn get-type-info
  [t]
  (assm/get-type (.-fqn t)))

(defn- props
  [x pred?]
  (into #{}
        (comp (filter pred?)
              (map :name)
              (map keyword))
        (assm/properties (.-fqn x))))

(deftype JsiiObject [fqn interfaces objId]
  clojure.lang.ILookup
  (valAt [this k]
    (let [valid-props (props this (complement :static))
          value
          (or (valid-props k)
              (throw (ex-info "Invalid property"
                              {:k           k
                               :valid-props valid-props})))]
      (->clj (client/get-property-value objId (name value)))))

  Invocable
  (-invoke [_ {:keys [op args]}]
    (->clj (client/call-method objId (name op) (or args []))))

  java.lang.Object
  (toString [this]
    (when (some (comp #{"toString"} :name)
                (:methods (get-type-info this)))
      (-invoke this {:op :toString})))

  json/JSONWriter
  (-write [object out]
    (.write out (format "{\"$jsii.byref\": \"%s\"}" objId))))

(defmethod ->type "$jsii.byref"
  [x]
  (let [id         (-> x first second)
        interfaces (get x "$jsii.interfaces" [])
        ref        (JsiiObjectRef/fromObjId id)]
    (->JsiiObject (.getFqn ref) interfaces id)))

(defmethod print-method JsiiObject
  [this w]
  (.write w (format "#jsii-object[%s]"
                    (pr-str {:id         (.-objId this)
                             :interfaces (.-interfaces this)
                             :props      (props this (complement :static))}))))

(defn- call-initializer
  [this args]
  (let [sym  (fqn/fqn->qualified-symbol (.-fqn this) "impl" "-initializer")
        ctor (requiring-resolve sym)]
    (apply ctor args)))

(deftype JsiiClass [fqn]
  clojure.lang.ILookup
  (valAt [this k]
    (let [valid-props (props this :static)
          value
          (or (valid-props k)
              (throw (ex-info "Invalid property"
                              {:k           k
                               :valid-props valid-props})))]
      (->clj (client/get-static-property-value fqn (name value)))))

  Invocable
  (-invoke [_ {:keys [op args]}]
    (->clj (client/call-static-method fqn (name op) (or args []))))

  clojure.lang.IFn
  (applyTo [this arglist]
    (call-initializer this arglist))
  (invoke [this]
    (call-initializer this []))
  (invoke [this a1]
    (call-initializer this [a1]))
  (invoke [this a1 a2]
    (call-initializer this [a1 a2]))
  (invoke [this a1 a2 a3]
    (call-initializer this [a1 a2 a3])))

(defmethod print-method JsiiClass
  [this w]
  (.write w (format "#jsii-class[%s]"
                    (pr-str {:fqn   (.-fqn this)
                             :props (props this :static)}))))

(deftype JsiiEnumMember [fqn value]
  json/JSONWriter
  (-write [object out]
    (.write out (format "{\"$jsii.enum\": \"%s\"}"
                        (str fqn "/" (name value))))))

(defmethod print-method JsiiEnumMember
  [this w]
  (.write w (format "#jsii-enum-member[%s]"
                    (pr-str (str (.-fqn this) "/" (name (.-value this)))))))

(declare member-values)

(deftype JsiiEnumClass [fqn]
  clojure.lang.ILookup
  (valAt [_ k]
    (let [valid-values (member-values fqn)
          value
          (or (valid-values k)
              (throw (ex-info "Invalid enumeration value"
                              {:k            k
                               :valid-values valid-values})))]
      (JsiiEnumMember. fqn value))))

(defmethod print-method JsiiEnumClass
  [this w]
  (.write w (format "#jsii-enum-class[%s]"
                    (pr-str {:fqn     (.-fqn this)
                             :members (member-values (.-fqn this))}))))

;;------------------------------------------------------------------------------

(defn create
  [c args]
  (let [fqn        (.-fqn c)
        object     (client/create-object fqn (or args []))
        interfaces (get object "$jsii.interfaces" [])
        oid        (get object "$jsii.byref")]
    (->JsiiObject fqn interfaces oid)))

(defn get-class
  [fqn]
  (let [{:keys [kind]}
        (or (assm/get-type fqn)
            (throw (Exception.
                     (str "No class available for fqn: " fqn))))]
    (case kind
      "enum"  (->JsiiEnumClass fqn)
      "class" (->JsiiClass fqn))))

(defn member-values
  [fqn]
  (->> (:members (assm/get-type fqn))
       (map :name)
       (map keyword)
       (set)))

(defn class-instance?
  [fqn x]
  (boolean
    (and (instance? JsiiObject x)
         (some #{fqn} (assm/class-heirarchy (.-fqn x))))))

(defn enum-member?
  [fqn x]
  (boolean
    (and (instance? JsiiEnumMember x)
         (= fqn (.-fqn x))
         ((member-values fqn) (.-value x)))))

(defn satisfies-interface?
  [fqn x]
  (boolean
    (and (instance? JsiiObject x)
         (let [interfaces (.-interfaces x)]
           (some #{fqn} (into #{}
                              (mapcat assm/interfaces)
                              (conj interfaces (.-fqn x))))))))
