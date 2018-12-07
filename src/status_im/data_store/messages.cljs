(ns status-im.data-store.messages
  (:require [cljs.tools.reader.edn :as edn]
            [re-frame.core :as re-frame]
            [status-im.constants :as constants]
            [status-im.data-store.realm.core :as core]
            [status-im.utils.core :as utils]
            [status-im.js-dependencies :as dependencies]))

(defn- transform-message [message]
  (-> message
      (update :message-type keyword)
      (update :content edn/read-string)))

(defn get-by-message-id
  [message-id]
  (if-let [js-message (.objectForPrimaryKey @core/account-realm "message" message-id)]
    (-> js-message
        (core/realm-obj->clj :message)
        transform-message)))

(defn get-parent-tree [messages message-id]
  (if-let [message (when-not (get messages message-id)
                     (get-by-message-id message-id))]
    (cond-> (assoc messages message-id message)
      (:parent message)
      (get-parent-tree messages (:parent message)))
    messages))

(defn get-parents [messages]
  (reduce (fn [acc [message-id {:keys [parent]}]]
            (if parent
              (get-parent-tree acc message-id)
              acc))
          messages
          messages))

(defn- get-messages
  ([]
   (get-messages 0))
  ([from]
   (let [messages (-> (core/get-by-field @core/account-realm :message :seen false)
                      (core/sorted :timestamp :desc)
                      #_(core/page from (+ from constants/default-number-of-messages))
                      (core/all-clj :message))]
     (get-parents
      (reduce (fn [acc {:keys [message-id] :as message}]
                (assoc acc message-id (transform-message message)))
              {}
              messages)))))

(def default-values
  {:to nil})

(re-frame/reg-cofx
 :data-store/get-messages
 (fn [cofx _]
   (assoc cofx :get-stored-messages get-messages)))

(re-frame/reg-cofx
 :data-store/get-message-by-message-id
 (fn [cofx _]
   (assoc cofx :get-stored-message-by-message-id get-by-message-id)))

(defn- sha3 [s]
  (.sha3 dependencies/Web3.prototype s))

(defn- prepare-content [content]
  (if (string? content)
    content
    (pr-str content)))

(defn- prepare-message [message]
  (utils/update-if-present message :content prepare-content))

(defn save-message-tx
  "Returns tx function for saving message"
  [{:keys [message-id from] :as message}]
  (fn [realm]
    (println message)
    (core/create realm
                 :message
                 (prepare-message message)
                 true)))

(defn delete-message-tx
  "Returns tx function for deleting message"
  [message-id]
  (fn [realm]
    (when-let [message (core/get-by-field realm :message :message-id message-id)]
      (core/delete realm message)
      (core/delete realm (core/get-by-field realm :user-status :message-id message-id)))))

(defn delete-messages-tx
  "Returns tx function for deleting messages with user statuses for given chat-id"
  [chat-id]
  (fn [realm]
    (core/delete realm (core/get-by-field realm :message :chat-id chat-id))
    (core/delete realm (core/get-by-field realm :user-status :chat-id chat-id))))

(defn message-exists? [message-id]
  (if @core/account-realm
    (not (nil? (.objectForPrimaryKey @core/account-realm "message" message-id)))
    false))
