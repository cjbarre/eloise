(ns eloise.commands.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::id uuid?)
(s/def ::action qualified-keyword?)
(s/def ::data (s/keys))
(s/def ::client-id uuid?)

(s/def ::command (s/keys :req-un [::id ::action ::data]
                         :opt-un [::client-id]))

(s/def ::parent uuid?)
(s/def ::event (s/merge ::command (s/keys :req-un [::parent])))