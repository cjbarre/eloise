(ns eloise.commands.core
  (:require 
   ;;Clojure
   [clojure.spec.alpha :as spec]
   [clojure.core.async :as async]
   ;;Pedestal
   [io.pedestal.http :as http]
   [io.pedestal.http.route :as route]
   [io.pedestal.http.body-params :refer [body-params]]
   ;;Jackdaw
   [jackdaw.client :as jc]
   [jackdaw.client.log :as jc-log]
   [jackdaw.serdes :refer [string-serde edn-serde]]
   ;;Eloise
   [eloise.commands.specs :as specs]
   [eloise.specs.core :as core-specs]))

;; Kafka Consumer 
;; 

(def consumer-config
  {"bootstrap.servers" "localhost:9092"
   "group.id"  "com.eloise.commands-consumer"})

(defonce consumer-state (atom nil))

(defn start-dev-consumer []
  (let [event-chan (async/chan (async/sliding-buffer 1))
        event-pub (async/pub event-chan :parent-id)
        thread (future
                 (with-open [consumer (jc/consumer consumer-config
                                                   {:key-serde (edn-serde)
                                                    :value-serde (edn-serde)})]
                   (.subscribe consumer (re-pattern "(.+\\.)(events|invalid-commands)"))
                   (println "Consumer Started")
                   (doseq [event (jc-log/log consumer 10)]
                     (async/>!! event-chan (:value event)))))]
    (reset! consumer-state {:consumer-thread thread
                            :event-chan event-chan
                            :event-pub event-pub})))

(defn stop-dev-consumer []
  (future-cancel (:consumer-thread @consumer-state))
  (async/close! (:event-chan @consumer-state)))

;; Kafka Producer 

(def producer-config
  {"bootstrap.servers" "localhost:9092"
   "acks" "all"
   "client.id" "com.eloise.commands"
   "max.block.ms" "100"}) 

(defonce producer (atom nil))

(defn start-dev-producer []
  (reset! producer
          (jc/producer producer-config
                       {:key-serde (edn-serde)
                        :value-serde (edn-serde)})))

(defn stop-dev-producer []
  (.close @producer))

;; Interceptors

(def pull-command-to-top-level
  {:name :pull-command-to-top-level
   :enter
   (fn [context]
     (assoc context :command (get-in context [:request :edn-params :command])))})

(def validate-generic-command
  {:name :validate-generic-command
   :enter
   (fn [{:keys [command] :as context}]
     (if-let [ed (spec/explain-data ::core-specs/command command)]
       (assoc context :response {:status 400
                                 :headers {}
                                 :body (str {:error :invalid-command
                                             :description "Invalid Command"
                                             :cause ed})})
       context))})

(def attach-producer
  {:name :attach-producer
   :enter
   (fn [context]
     (assoc context :producer producer))})

(def journal-command
  {:name :journal-command
   :enter 
   (fn [{:keys [producer command] :as context}]
     (try
       @(jc/produce! @producer
                     {:topic-name (str (namespace (:action command)) ".commands")}
                     command)
       context
       (catch Exception e
         (assoc context :response {:status 500
                                   :headers {}
                                   :body {:error :unable-to-send-command
                                          :description "Unable to send command"
                                          :cause (format "Does topic %s exist?" 
                                                         (str (namespace (:action command)) ".commands"))}}))))})

;; Event Channel

(def attach-event-chan
  {:name :attach-event-pub
   :enter
   (fn [{:keys [command] :as context}]
     (let [event-chan (async/chan (async/sliding-buffer 1))]
       (async/sub (:event-pub @consumer-state) (:id command) event-chan)
       (assoc context :event-chan event-chan)))})

(def wait-for-event
  {:name :wait-for-event
   :enter
   (fn [{:keys [event-chan command] :as context}]
     (println "Waiting for event")
     (async/go
       (assoc context :event (async/<! event-chan))))})

(def cleanup-event-chan
  {:name :cleanup-event-chan
   :enter
   (fn [{:keys [command event-chan] :as context}]
     (async/unsub-all (:event-pub @consumer-state) (:id command))
     (async/close! event-chan)
     context)})

(def respond
  {:name :respond
   :enter
   (fn [context]
     (assoc context :response {:status 200 
                               :headers {} 
                               :body (:event context)}))})

(def commands-interceptor [(body-params) 
                           pull-command-to-top-level 
                           validate-generic-command
                           attach-producer
                           journal-command
                           attach-event-chan
                           wait-for-event
                           cleanup-event-chan
                           respond])

(def routes
  (route/expand-routes
   #{["/commands" :post commands-interceptor :route-name :commands]}))

;; Kafka



;; HTTP Server

(def service-map
  {::http/routes routes
   ::http/type :jetty
   ::http/port 8890})

(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))

(defn stop-dev []
  (http/stop @server))

(defn restart-dev []
  (stop-dev)
  (start-dev))


(restart-dev)
 

(comment



  (start-dev-producer)

  (stop-dev-producer)

  (start-dev-consumer)



  (stop-dev-consumer)

  @(jc/produce! @producer {:topic-name "move.events"} {:parent-id #uuid "94f2d236-3be0-45c0-97ff-5b54941caa08"
                                                       :action :move/profile-created
                                                       :data {}})

  (async/poll! (:event-chan @consumer-state))

  (def chan (async/go-loop [a 1] 1))

  (async/poll! chan))

