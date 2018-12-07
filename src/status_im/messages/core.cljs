(ns status-im.messages.core
  (:require [status-im.accounts.db :as accounts.db]
            [status-im.utils.fx :as fx]
            [status-im.data-store.messages :as data-store]))

(fx/defn load-messages
  [{:keys [db get-stored-messages get-messages-user-statuses]
    :as cofx}]
  (let [chats (:chats db)
        public-key (accounts.db/current-public-key cofx)
        messages (get-stored-messages)
        #_#_statuses (get-messages-user-statuses (map :message-id messages))]
    {:db (assoc db
                :messages
                messages)}))

(fx/defn message-seen
  [{:keys [db] :as cofx} message-id]
  {:db (assoc-in db [:messages message-id :seen] true)})
