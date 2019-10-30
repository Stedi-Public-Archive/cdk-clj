(ns stedi.jsii.import
  (:require [clojure.spec.test.alpha :as stest]
            [clojure.string :as string]
            [stedi.jsii.types :as types]
            [stedi.jsii.spec]))

(defn- fqn->ns-sym
  [fqn]
  (-> fqn
      (string/replace "@" "")
      (string/replace "/" ".")
      (string/split #"\.")
      ((partial string/join "."))
      (symbol)))

(defn import-fqn
  [fqn alias-sym]
  (let [target-ns (-> fqn (fqn->ns-sym) (create-ns))
        ns-sym    (ns-name target-ns)

        c                 (types/get-class fqn)
        {:keys [methods
                members]} (types/get-type-info c)]
    (ns-unmap *ns* alias-sym)
    (ns-unmap target-ns alias-sym)
    (let [fqs
          (symbol
            (intern target-ns '-initializer
                    (fn [& args]
                      (types/create c args))))]
      (stest/instrument fqs)
      (intern target-ns alias-sym c))
    (refer ns-sym :only [alias-sym])
    (doseq [{:keys [static] :as method} methods]
      (let [method-sym (-> method (:name) (symbol))
            fqs
            (symbol
              (intern target-ns method-sym
                      (fn [& args]
                        (types/-invoke
                          (if static c (first args))
                          {:op   method-sym
                           :args (if static args (rest args))}))))]
        (stest/instrument fqs)))
    (doseq [member members]
      (let [member-sym  (-> member (:name) (symbol))
            member-k    (keyword member-sym)
            enum-member (get c member-k)]
        (intern target-ns member-sym enum-member)))
    (alias alias-sym ns-sym)))
