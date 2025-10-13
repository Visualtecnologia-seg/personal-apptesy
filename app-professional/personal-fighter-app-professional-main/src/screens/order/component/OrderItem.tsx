import React from "react";
import {StyleSheet, View} from "react-native";
import {Theme} from "../../../theme";
import {OrderModel} from "../../../model";
import {OrderItemConfirmed} from "./OrderItemConfirmed";
import {OrderItemDone} from "./OrderItemDone";
import {OrderItemCancelled} from "./OrderItemCancelled";
import {HomeItemOpen} from "../../home/component/HomeItemOpen";

type TProps = {
  order?: OrderModel;
  loading?: boolean;
  onRefresh?: () => void;
  onCancelOrder?: (order) => void;
  onConfirm?: any;
  onDecline?: any;
};

export const OrderItem: React.FC<TProps> = props => {
  const order = props.order;

  return (
    <View style={styles.itemRowContainer}>
      {order.status === "CONFIRMED" && <OrderItemConfirmed order={order} onCancelOrder={props.onCancelOrder}/>}
      {order.status === "OPEN" &&
      <HomeItemOpen order={order} onRefresh={props.onRefresh}/>}
      {order.status === "BLOCKED" &&
      <HomeItemOpen order={order} onRefresh={props.onRefresh}/>}
      {order.status === "DONE" && <OrderItemDone order={order} onRefresh={props.onRefresh}/>}
      {order.status === "CANCELLED" && <OrderItemCancelled order={order}/>}
    </View>
  );
};

const styles = StyleSheet.create({
  itemRowContainer: {
    backgroundColor: Theme.card.backgroundColor,
    marginHorizontal: 16,
    paddingHorizontal: 16,
    shadowColor: Theme.shadowColor,
    shadowOffset: {
      width: 0,
      height: 2
    },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 1,
    marginBottom: 12,
    borderRadius: 8
  }
});
