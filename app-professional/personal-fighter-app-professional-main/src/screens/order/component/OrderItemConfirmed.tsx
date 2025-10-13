// @ts-nocheck
import React from "react";
import {Image, StyleSheet, Text, TouchableOpacity, View} from "react-native";
import {Theme, YELLOW} from "../../../theme";
import {Divider} from "../../../components/general/divider";
import {Button} from "../../../components/general/buttons";
import {OrderModel} from "../../../model";
import {Feather} from "@expo/vector-icons";
import {infoOrder} from "../../../utils/InfoOrder";
import {useNavigation} from "@react-navigation/native";
import {NavigationNames} from "../../../navigations/NavigationNames";
import dayjs from "dayjs";

type TProps = {
  order: OrderModel;
  onCancelOrder: (order) => void;
};

export const OrderItemConfirmed: React.FC<TProps> = props => {
  const navigation = useNavigation();
  const order = props.order;
  const info = infoOrder(order.numberOfCustomers);

  return (
    <>
      <View style={{flexDirection: "row", marginBottom: 16, marginTop: 16}}>
        <View style={styles.nextButton}>
          <Text style={{color: Theme.textColor.dark, fontSize: Theme.textSize.secondary}}>Confirmada</Text>
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
      <Divider/>
      <View style={{flexDirection: "row", marginBottom: 16, marginTop: 5}}>
        <Image
          style={styles.image}
          source={order?.customer?.avatarUrl ? {uri: order?.customer?.avatarUrl} : require("../../../../assets/user.png")}
        />
        <View style={{flex: 1, alignItems: "flex-start", marginLeft: 10}}>
          <Text style={{
            fontSize: Theme.textSize.primary,
            color: Theme.textColor.yellow
          }}>Aluno: {order.customer.name}</Text>
          <Text style={{
            fontSize: Theme.textSize.secondary,
            color: Theme.textColor.secondary
          }}>Aula: {order.product.name}</Text>
        </View>
        <TouchableOpacity
          onPress={() => navigation.navigate(NavigationNames.ChatScreen, {order: order})}
          style={{justifyContent: "center", flexDirection: "row"}}
        >
          <Feather
            name="message-circle"
            size={32}
            color={Theme.iconColor}
            style={{marginLeft: 16}}
          />
        </TouchableOpacity>
      </View>
      <Button
        title={"Cancelar agendamento"}
        style={{backgroundColor: Theme.nextButton.colors.secondary, marginBottom: 16}}
        textColor={{color: Theme.textColor.primary}}
        onPress={() => props.onCancelOrder(order)}
      />
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
    backgroundColor: Theme.nextButton.colors.confirmed
  },
  text: {
    color: Theme.textColor.primary,
    fontSize: Theme.textSize.primary
  }
});
