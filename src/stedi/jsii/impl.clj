(ns stedi.jsii.impl
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.walk :as walk]
            [stedi.jsii.assembly :as assm]
            [stedi.jsii.client :as client]
            [stedi.jsii.fqn :as fqn])
  (:import (software.amazon.jsii JsiiObjectRef)))

(defn- jsii-type?
  [x]
  (and (map? x)
       (string? (ffirst x))
       (.startsWith (ffirst x) "$jsii")))

(defmulti ^:private ->type ffirst)

(defn- ->clj
  [m]
  (walk/postwalk
    (fn [x]
      (if (jsii-type? x)
        (->type x)
        x))
    m))

(defprotocol Invocable
  (-invoke [_ req]))

(defn get-type-info
  [t]
  (assm/get-type (.-fqn t)))

(defn- props
  [x]
  (into #{}
        (comp (map :name)
              (map keyword))
        (:properties (get-type-info x))))

(deftype JsiiObject [fqn objId]
  clojure.lang.ILookup
  (valAt [this k]
    (let [valid-props (props this)
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
  (let [id  (-> x first second)
        ref (JsiiObjectRef/fromObjId id)]
    (->JsiiObject (.getFqn ref) id)))

(defmethod print-method JsiiObject
  [this w]
  (.write w (format "#jsii-object[%s]"
                    (pr-str {:id    (.-objId this)
                             :props (props this)}))))

(defn- call-initializer
  [this args]
  (let [sym  (fqn/fqn->qualified-symbol (.-fqn this) "impl" "-initializer")
        ctor (requiring-resolve sym)]
    (apply ctor args)))

(deftype JsiiClass [fqn]
  clojure.lang.ILookup
  (valAt [this k]
    (let [valid-props (props this)
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
                             :props (props this)}))))

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

(declare get-class)

(defn- base-classes
  ([x] (base-classes x nil))
  ([x classes]
   (lazy-seq
     (let [classes' (conj classes (.-fqn x))]
       (if-let [base (:base (get-type-info x))]
         (base-classes (get-class base) classes')
         classes')))))

;;------------------------------------------------------------------------------

(defn create
  [c args]
  (let [fqn (.-fqn c)
        id  (client/create-object fqn (or args []))]
    (->JsiiObject fqn id)))

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
  (and (instance? JsiiObject x)
       (some #{fqn} (base-classes x))))

(defn enum-member?
  [fqn x]
  (and (instance? JsiiEnumMember x)
       (= fqn (.-fqn x))
       ((member-values fqn) (.-value x))))

(defn satisfies-interface?
  [fqn x]
  (and (instance? JsiiObject x)
       (or (= (.-fqn x) fqn)
           (let [{:keys [interfaces]} (get-type-info x)]
             (some #{fqn} interfaces)))))
