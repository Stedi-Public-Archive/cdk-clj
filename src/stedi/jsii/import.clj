(ns stedi.jsii.import
  (:require [clojure.spec.test.alpha :as stest]
            [stedi.jsii.fqn :as fqn]
            [stedi.jsii.impl :as impl]
            [stedi.jsii.spec]))

;; TODO: delay loading of specs until used

(defn- intern-initializer
  [impl-ns-sym alias-sym c]
  (ns-unmap impl-ns-sym alias-sym)
  (let [fqs
        (symbol
          (intern impl-ns-sym '-initializer
                  (fn [& args]
                    (impl/create c args))))]
    (stest/instrument fqs)
    (intern impl-ns-sym alias-sym c))
  (refer impl-ns-sym :only [alias-sym]))

(defn- intern-methods
  [target-ns-sym methods c]
  (doseq [{:keys [static] :as method} methods]
    (let [method-sym (-> method (:name) (symbol))
          fqs
          (symbol
            (intern target-ns-sym method-sym
                    (fn [& args]
                      (impl/-invoke
                        (if static c (first args))
                        {:op   method-sym
                         :args (if static args (rest args))}))))]
      (stest/instrument fqs))))

(defn- intern-enum-members
  [target-ns-sym members c]
  (doseq [member members]
    (let [member-sym  (-> member (:name) (symbol))
          member-k    (keyword member-sym)
          enum-member (get c member-k)]
      (intern target-ns-sym member-sym enum-member))))

(defn import-fqn
  [fqn alias-sym]
  (let [target-ns     (-> fqn (fqn/fqn->ns-sym) (create-ns))
        target-ns-sym (ns-name target-ns)
        impl-ns-sym   (ns-name (create-ns (symbol (str target-ns ".impl"))))
        c             (impl/get-class fqn)

        {:keys [methods members]} (impl/get-type-info c)]
    (ns-unmap *ns* alias-sym)
    (intern-initializer impl-ns-sym alias-sym c)
    (intern-methods target-ns-sym methods c)
    (intern-enum-members target-ns-sym members c)
    (alias alias-sym target-ns-sym)))
