(ns deathstar.spec
  #?(:cljs (:require-macros [deathstar.spec]))
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test.check.generators :as gen]))

(do (clojure.spec.alpha/check-asserts true))

(def ^:const OP :op)
(s/def ::out| any?)

(def op-specs
  {:host/extension-activate (s/keys :req-un [::op #_::out|])
   :host/extension-deactivate (s/keys :req-un [::op #_::out|])})

(def ch-specs
  {:extension-ops| #{:host/extension-activate
                     :host/extension-deactivate}})

(def op-keys (set (keys op-specs)))
(def ch-keys (set (keys ch-specs)))

(s/def ::op op-keys)

(s/def ::ch-exists ch-keys)
(s/def ::op-exists (fn [v] (op-keys (if (keyword? v) v (OP v)))))
(s/def ::ch-op-exists (s/cat :ch ::ch-exists :op ::op-exists))

(defmacro op
  [chkey opkey]
  (println 3)
  (s/assert ::ch-exists  chkey)
  (s/assert ::op-exists  opkey)
  `~opkey)

(defmacro vl
  [chkey v]
  (s/assert ::ch-exists  chkey)
  `~v)

#_(defmacro assert-op
    [chkey opkey]
    `(do
       (s/assert ::ch-exists  ~chkey)
       (s/assert ::op-exists  ~opkey)
       #_(when-not (opkeys ~opkey)
           (throw (Exception. "no such op")))))

;; (defmulti op-type OP)

;; (defmethod op-type :app.main/mount
;;   [vl]
;;   (s/keys :req [::op ::out|]))

;; (s/def ::op (s/multi-spec op-type OP))
;; 

#_(s/def ::ch-op-exists (fn [{:keys [channel-key val-map op-key]}]
                        ((channel-key channels) (or op-key (OP val-map)))))
#_(s/def ::val-map-valid (fn [{:keys [channel-key val-map]}]
                         (s/valid? ((OP val-map) ops) val-map)))

#_(s/valid? (:project.app.main/mount ops) {:op :project.app.main/mount})
#_(gen/generate (s/gen (:project.app.main/mount ops)))

#_(defmacro op
  [channel-key op-key]
  `~op-key)

#_(s/fdef op
    :args (s/and
           (s/cat :channel-key ::channel-exists
                  :op-key ::op-exists)
           ::channel-op-exists)
    :ret any?)

#_(defmacro vl
  [channel-key val-map]
  `~val-map)

#_(s/fdef vl
    :args (s/and
           (s/cat :channel-key ::channel-exists
                  :val-map ::op-exists)
           ::channel-op-exists
           ::val-map-valid)
    :ret any?)