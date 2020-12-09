(ns eloise.specs.core
  (:require [clojure.spec.alpha :as s]))


(s/def ::id uuid?)
(s/def ::action qualified-keyword?)
(s/def ::data (s/keys))
(s/def ::client-id string?)
(s/def ::parent-id ::id)
(s/def ::timestamp inst?)
(s/def ::version integer?)

(s/def ::command (s/keys :req-un [::id  
                                  ::timestamp 
                                  ::action 
                                  ::data 
                                  ::client-id]))

(s/def ::event (s/merge ::command (s/keys :req-un [::parent-id])))

(s/def ::entity (s/keys :req-un [::id ::version ::timestamp]))