(ns eloise.move.core
  (:require [jackdaw.streams :as streams]
            [jackdaw.serdes.edn :as serdes.edn]
            [willa.core :as w]
            [eloise.move.specs :as specs]
            [eloise.specs 
             [core :as core-specs]
             [move :as move-specs]]
            [clojure.spec.alpha :as s])
  (:import org.apache.kafka.streams.state.QueryableStoreTypes))



(def basic-workflow
  [[:input-topic :validate-command]
   [:validate-command :output-topic]])
 
(def validate-command-xform
  (map (fn [[k v]] 
         [k (assoc v
                   :parent-id (:id v)
                   :id (java.util.UUID/randomUUID)
                   :action :move/move-created)])))

(def validate-create-move-command
  (map (fn [[k command]]
         (if-let [ed (s/explain-data ::move-specs/create-move-command (:data command))]
           [k {:action :move/invalid-command
               :parent-id (:id command)
               :id (java.util.UUID/randomUUID)
               :explain-data ed}]
           [k command]))))

(def invalid
  (filter (fn [[k event]] (= :move/invalid-command (:action event)))))

(def valid 
  (filter (fn [[k command]] (not= :move/invalid-command (:action command)))))
 
(def command->event
  (map (fn [[_ command]]
         [(get-in command [:data :user-id])
          (assoc command
                 :id (java.util.UUID/randomUUID)
                 :parent-id (:id command)
                 :action :move/move-created
                 :data (assoc (:data command)
                              :id (java.util.UUID/randomUUID)
                              :version 1))])))

(def workflow [[:input-topic :validate-command]
               [:validate-command :invalid]
               [:validate-command :valid]
               [:invalid :output-invalid-command]
               [:valid :command->event]
               [:command->event :output-event]
               [:command->event :output-move-topic]
               [:output-move-topic :move-store]])

(def entities
  {:input-topic {::w/entity-type :topic
                 :topic-name "move.commands"
                 :key-serde (serdes.edn/serde)
                 :value-serde (serdes.edn/serde)}
   :validate-command {::w/entity-type :kstream
                      :willa.core/xform validate-create-move-command}
   :valid {::w/entity-type :kstream
           ::w/xform valid}
   :command->event {::w/entity-type :kstream
                    ::w/xform command->event}
   :output-event {::w/entity-type :topic
                  :topic-name "move.events"
                  :key-serde (serdes.edn/serde)
                  :value-serde (serdes.edn/serde)}
   :output-move-topic {::w/entity-type :topic
                       :topic-name "move"
                       :key-serde (serdes.edn/serde)
                       :value-serde (serdes.edn/serde)}
   :move-store {::w/entity-type :ktable
                :store-name "move-store"}
   :invalid {::w/entity-type :kstream
             ::w/xform invalid}
   :output-invalid-command {::w/entity-type :topic
                            :topic-name "move.invalid-commands"
                            :key-serde (serdes.edn/serde)
                            :value-serde (serdes.edn/serde)}})
     
;; App setup

(def app-config
  {"application.id" "com.eloise.move"
   "application.server" "host1:8888"
   "bootstrap.servers" "localhost:9092"
   "processing.guarantee" "exactly_once"
   "cache.max.bytes.buffering" "0"})

(def topology
  {:workflow workflow
   :entities entities})


(defn start! []
  (let [builder   (doto (streams/streams-builder) ;; step 1
                    (w/build-topology! topology)) ;; step 2
        kstreams-app (streams/kafka-streams builder app-config) ;; step 3
        ]
    (streams/start kstreams-app) ;; step 4
    kstreams-app))

;; Runtime 

(defonce app (atom nil))

(defn start [] (reset! app (start!)))

(defn stop [] (streams/close @app))

(defn restart [] 
  (stop)
  (start)
  
  )
  

(type @app)

#_(restart)

(comment
  (start)



  (def move-store (.store @app "mnever also " (QueryableStoreTypes/keyValueStore)))

  (type move-store)

  (.get move-store #uuid "2a451fd7-547c-44de-9b73-5d8752df6ff1")

  (with-open [iterator (.all move-store)]
    (let [s (iterator-seq iterator)]
      (doseq [item s]
        (println item))))

  (.allMetadataForStore @app "move-store")
  
  )

