(ns stedi.jsii.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [stedi.jsii.assembly :as assm]
            [stedi.jsii.impl :as impl]
            [stedi.jsii.fqn :as fqn]))

(s/def ::string-like
  (s/or :string string?
        :keyword keyword?))

(s/def ::json
  (s/or :string ::string-like
        :int integer?
        :float float?
        :boolean boolean?
        :vector (s/coll-of ::json-value :kind vector?)
        :map (s/map-of ::string-like ::json-value)))

(defn- metatype
  [{:keys [datatype kind]}]
  (cond
    datatype :datatype
    :else    (keyword kind)))

(defmulti ^:private spec-form ffirst)

(defmethod spec-form :union
  [{:keys [union]}]
  (let [{:keys [types]} union]
    `(s/or ~@(mapcat (juxt (constantly :opt)
                           spec-form)
                     types))))

(defmethod spec-form :fqn
  [{:keys [fqn]}]
  (fqn/fqn->qualified-keyword fqn))

(defmethod spec-form :primitive
  [{:keys [primitive]}]
  (case primitive
    "string"  `string?
    "any"     `any?
    "boolean" `boolean?
    "number"  `number?
    "date"    `inst?
    "json"    ::json))

(defmethod spec-form :collection
  [{:keys [collection]}]
  (let [{:keys [elementtype kind]} collection]
    (let [element-form (spec-form elementtype)]
      (case kind
        "map"   `(s/map-of ::string-like ~element-form)
        "array" `(s/coll-of ~element-form :kind vector?)))))

(defn- prop-spec-k
  [t prop]
  (fqn/fqn->qualified-keyword (:fqn t) (:name prop)))

(defn gen-class-instance
  [fqn]
  (sgen/return (impl/->JsiiObject fqn nil)))

(defn- class-spec-definition
  [{:keys [fqn]}]
  `(s/def ~(fqn/fqn->qualified-keyword fqn)
     (s/spec (partial impl/class-instance? ~fqn)
             :gen (partial gen-class-instance ~fqn))))

(defn gen-enum-member
  [fqn]
  (sgen/bind
    (sgen/elements (impl/member-values fqn))
    (fn [value]
      (sgen/return
        (impl/->JsiiEnumMember fqn value)))))

(defn- enum-spec-definition
  [{:keys [fqn]}]
  `(s/def ~(fqn/fqn->qualified-keyword fqn)
     (s/spec (partial impl/enum-member? ~fqn)
             :gen (partial gen-enum-member ~fqn))))

(defn gen-satisfies-interface
  [fqn]
  (sgen/return
    (impl/->JsiiObject fqn nil)))

(defn- interface-spec-definition
  [{:keys [fqn]}]
  `(s/def ~(fqn/fqn->qualified-keyword fqn)
     (s/spec (partial impl/satisfies-interface? ~fqn)
             :gen (partial gen-satisfies-interface ~fqn))))

(defn- datatype-spec-definition
  [{:keys [fqn properties] :as t}]
  (let [req-un (into []
                     (comp (remove :optional)
                           (map (partial prop-spec-k t)))
                     properties)
        opt-un (into []
                     (comp (filter :optional)
                           (map (partial prop-spec-k t)))
                     properties)]
    `(s/def ~(fqn/fqn->qualified-keyword fqn)
       (s/keys :req-un ~req-un
               :opt-un ~opt-un))))

(defn- method-arity-form
  [parameters]
  `(s/cat ~@(mapcat (juxt (comp keyword :name)
                          (fn [{:keys [optional] :as param}]
                            (let [form (spec-form (:type param))]
                              (if optional
                                `(s/nilable ~form)
                                form))))
                    parameters)))

(defn- method-arg-spec-form
  [{:keys [fqn] :as t} {:keys [static parameters] :as method}]
  (let [arities* (assm/arities
                   (concat (when-not static
                             (list {:name "this"
                                    :type {:fqn fqn}}))
                           parameters))]
    (if (= 1 (count arities*))
      (method-arity-form parameters)
      `(s/alt
         ~@(mapcat
             (fn [arity-params]
               [(keyword (str "arity" (count arity-params)))
                (method-arity-form arity-params)])
             arities*)))))

(defn- method-spec-definition
  [{:keys [fqn] :as t} {:keys [name returns] :as method}]
  `(s/fdef ~(fqn/fqn->qualified-symbol fqn name)
                :args ~(method-arg-spec-form t method)
                :ret  ~(or (some-> returns (:type) (spec-form)) `nil?)))

(defn- prop-spec-definition
  [t prop]
  `(s/def ~(prop-spec-k t prop)
     ~(spec-form (:type prop))))

(defn- initializer-spec-definition
  [{:keys [fqn] :as t} method]
  `(s/fdef ~(fqn/fqn->qualified-symbol fqn "impl" "-initializer")
     :args ~(method-arg-spec-form t (assoc method :static true))
     :ret  ~(fqn/fqn->qualified-keyword fqn)))

(defn- class-initializer-spec-definition
  [{:keys [fqn name] :as t} method]
  `(s/fdef ~(fqn/fqn->qualified-symbol fqn "impl" name)
     :args ~(method-arg-spec-form t (assoc method :static true))
     :ret  ~(fqn/fqn->qualified-keyword fqn)))

(defn- spec-definitions
  [t]
  (try
    (let [{:keys [initializer methods properties]} t

          type-definition
          (case (metatype t)
            :class     (class-spec-definition t)
            :enum      (enum-spec-definition t)
            :datatype  (datatype-spec-definition t)
            :interface (interface-spec-definition t))

          initializer-definition            (initializer-spec-definition t initializer)
          class-initializer-spec-definition (class-initializer-spec-definition t initializer)]
      (doall
        (concat (list type-definition
                      initializer-definition
                      class-initializer-spec-definition)
                (map (partial method-spec-definition t) methods)
                (map (partial prop-spec-definition t) properties))))
    (catch Throwable e
      (throw (ex-info "Unable to build spec definition" {:t t} e)))))

(defn- index-spec-forms
  []
  (into {}
        (comp (mapcat spec-definitions)
              (map (juxt second identity)))
        (assm/all-types)))

(defonce ^:private indexed-specs (index-spec-forms))

(defn load-spec
  [k]
  (or (s/get-spec k)
      (let [definition (or (get indexed-specs k)
                           (throw (Exception. (str "No spec for k " k))))
            deps       (->> (drop 2 definition)
                            (tree-seq seqable? seq)
                            (filter qualified-keyword?))]
        ;; This is a little bit of a hack, it is possible for specs to
        ;; have circular dependencies so we load the current spec with
        ;; a placeholder before loading its dependencies.
        (eval `(s/def ~k any?))
        (dorun (map load-spec deps))
        (eval definition)))
  true)
