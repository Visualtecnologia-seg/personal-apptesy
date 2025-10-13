// @ts-nocheck
import React from "react";
import {StyleSheet, Text, View} from "react-native";
import {Theme, YELLOW} from "../../../theme";
import {OrderModel} from "../../../model";
import {Feather} from "@expo/vector-icons";
import {infoOrder} from "../../../utils/InfoOrder";
import dayjs from "dayjs";

type TProps = {
  order: OrderModel;
};

export const OrderItemCancelled: React.FC<TProps> = props => {
  const order = props.order;
  const info = infoOrder(order.numberOfCustomers);
  return (
    <>
      <View style={{flexDirection: "row", marginBottom: 16, marginTop: 16}}>
        <View style={styles.nextButton}>
          <Text style={{color: Theme.textColor.secondary, fontSize: Theme.textSize.secondary}}>Cancelada</Text>
        </View>
        <View style={{flex: 1, justifyContent: "center", alignItems: "flex-end"}}>
          <Text style={{
            fontSize: Theme.textSize.primary,
            color: Theme.textColor.yellow
          }}>{dayjs(order.date).format("DD/MM")} às {order.startTime.substring(0, 5)}</Text>
        </View>
      </View>
      <View style={{flexDirection: "row"}}>
        <View style={{flex: 1}}>
          <Text key={order.address.id}
                style={{
                  fontSize: Theme.textSize.primary,
                  color: Theme.textColor.primary,
                  marginBottom: 5
                }}>{order.address.street}, {order.address.number}
          </Text>
        </View>
        <View>
          <Text style={[styles.text, {textAlign: "right"}]}>R$ {info.price},00</Text>
        </View>
      </View>
      <View style={{flexDirection: "row", justifyContent: "center", marginVertical: 10}}>
        <Feather name={info.icon} size={20} color={YELLOW}/>
        <Text style={[styles.text, {textAlign: "right", marginLeft: 10}]}>{info.group} - {order.product.name}</Text>
      </View>
    </>
  );
};

const styles = StyleSheet.create({
  leftContainer: {
    alignSelf: "center",
    width: 25
  },
  centerContainer: {
    paddingHorizontal: 16,
    flex: 1,
    justifyContent: "center",
    alignItems: "flex-start"
  },
  rightContainer: {
    alignSelf: "center"
  },
  title: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary,
    textAlign: "left"
  },
  image: {
    width: 50,
    height: 50,
    borderRadius: 25,
    borderWidth: 2,
    borderColor: Theme.imageBorderColor
  },
  nextButton: {
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: 4,
    width: 110,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: Theme.nextButton.colors.canceled
  },
  text: {
    color: Theme.textColor.primary,
    fontSize: Theme.textSize.primary
  }
});
