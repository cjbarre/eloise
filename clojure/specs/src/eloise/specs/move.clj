(ns eloise.specs.move
  (:require [clojure.spec.alpha :as s]
            [eloise.specs.core :as c]))

(def example-create-move
  {:id #uuid "4a451fd7-547c-44de-9b73-5d8752df6ff1"
   :version 1
   :timestamp #inst "2021-04-19T00:00:00"
   :user-id #uuid "4a451fd7-547c-44de-9b73-5d8752df6ff1"
   :moving-day #inst "2021-04-19T00:00:00"
   :preferred-zipcodes #{"97215" "97216"}
   :ideal-max-price-cents 157000
   :acceptable-max-price-cents 190000
   :amenities {:need #{"Washer/Dryer" "1 Bedroom" "Microwave" "Good Hot Water" "Good Water Pressure" "Large Windows" "Bathtub"}
               :want #{"Dishwasher" "Yard / Garden" "Groundfloor" "Quiet Street" "A/C"}
               :bonus #{"Clawfoot Tub" "Wood Floors" "High Ceilings" "Fireplace"}}
   :properties #{}})

(s/def ::user-id ::c/id)
(s/def ::moving-day ::c/timestamp)
(s/def ::preferred-zipcodes (s/coll-of string?))
(s/def ::ideal-max-price-cents number?)
(s/def ::acceptable-max-price-cents number?)
(s/def ::need (s/coll-of string?))
(s/def ::want (s/coll-of string?))
(s/def ::bonus (s/coll-of string?))
(s/def ::amenities (s/keys :req-un [::need ::want ::bonus]))

(s/def ::move (s/merge ::c/entity
                       (s/keys :req-un [::user-id
                                        ::moving-day
                                        ::preferred-zipcodes
                                        ::ideal-max-price-cents
                                        ::acceptable-max-price-cents
                                        ::amenities
                                        ::properties])))

(s/def ::create-move-command ::move)