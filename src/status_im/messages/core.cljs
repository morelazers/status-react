(ns status-im.messages.core
  (:require [status-im.accounts.db :as accounts.db]
            [status-im.utils.fx :as fx]))

(fx/defn load-messages
  [{:keys [db get-stored-messages get-messages-user-statuses get-referenced-messages]
    :as cofx}]
  (let [chats (:chats db)
        public-key (accounts.db/current-public-key cofx)
        messages (get-stored-messages)
        statuses (get-messages-user-statuses (map :message-id messages))]
    {:db (assoc db
                :messages
                (reduce (fn [acc {:keys [message-id] :as message}]
                          (assoc acc
                                 message-id
                                 (assoc message
                                        :user-statuses
                                        (get statuses message-id))))
                        {}
                        messages))}))
