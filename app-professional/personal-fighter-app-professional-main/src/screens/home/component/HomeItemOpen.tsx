// @ts-nocheck
import React, {useEffect, useState} from "react";
import {StyleSheet, Text, View} from "react-native";
import {Feather} from "@expo/vector-icons";
import moment from "moment";
import {OrderModel} from "../../../model";
import {infoOrder} from "../../../utils/InfoOrder";
import {Theme, YELLOW} from "../../../theme";
import {Button} from "../../../components";
import {confirmOrder, declineOrder} from "../../../services/OrderService";
import OrderConfirmModal from "../../order/modal/OrderConfirmModal";
import {useIsFocused} from "@react-navigation/native";
import dayjs from "dayjs";
import {useAuth} from "../../../context/AuthContext";


type TProps = {
  order?: OrderModel;
  onRefresh?: () => void;
  onConfirm?: (order) => void;
  onDecline?: (order) => void
};

export const HomeItemOpen: React.FC<TProps> = props => {
  /* Default */
  const isFocused = useIsFocused();
  const {user} = useAuth();
  const [loading, setLoading] = useState(true);
  const [visible, setVisible] = useState(false);
  const [order, setOrder] = useState<OrderModel>(null);
  const info = infoOrder(order?.numberOfCustomers);

  useEffect(() => {
    setOrder(props.order);
  }, []);

  function onConfirmOrder() {
    setVisible(true);
    order.professional = {id: user.id};
    setOrder(order);
  }

  function onDeclineOrder() {
    setLoading(true);
    order.professional = {id: user.id};
    declineOrder(order, user.id)
      .then(res => {
        if (!res.error) {
          setVisible(false);
          props.onRefresh();
        }
      });
  }

  function handleConfirmOrder(confirm) {
    if (confirm) {
      confirmOrder(order, user.id)
        .then(res => {
          if (!res.error) {
            setVisible(false);
            props.onRefresh();
          }
        });
    } else {
      setVisible(false);
    }
  }

  return (
    <>
      <View style={{flexDirection: "row", marginBottom: 16, marginTop: 16}}>
        <View>
          <Text style={styles.titleText}>
            {dayjs(order?.date).format("DD/MM")} às {order?.startTime.substring(0, 5)}</Text>
        </View>
        <View style={{flex: 1, justifyContent: "center", alignItems: "flex-end"}}>
          <Text style={[styles.titleText, {textAlign: "right"}]}>R$ {info.price},00</Text>
        </View>
      </View>
      <View style={{flexDirection: "row"}}>
        <View style={{flex: 1}}>
          <Text
            key={order?.address?.id}
            style={{
              fontSize: Theme.textSize.primary,
              color: Theme.textColor.yellow,
              marginBottom: 5
            }}>Aluno: {order?.customer?.name}
          </Text>
          <Text
            key={order?.address?.id}
            style={styles.profileText}>Sexo: {order?.customer?.gender === "MALE" ? "Masculino" : "Feminino"}
          </Text>
          <Text
            key={order?.address?.id}
            style={styles.profileText}>Idade: {moment().diff(order?.customer?.birthday, "years", false)} anos
          </Text>
        </View>
        <View style={{flex: 2, alignItems: "center"}}>
          <Text key={order?.address?.id}
                style={styles.addressText}>{order?.address?.street}, {order?.address?.number}
          </Text>
          <Text key={order?.address?.id} style={styles.addressText}>{order?.address?.complement}          </Text>
          <Text key={order?.address?.id}
                style={styles.addressText}>{order?.address?.city} - {order?.address?.state}
          </Text>
        </View>
      </View>
      <View style={{flexDirection: "row", justifyContent: "center", marginTop: 10}}>
        <Feather name={info.icon} size={20} color={YELLOW}/>
        <Text
          style={[styles.titleText, {textAlign: "right", marginLeft: 10}]}>{info.group} - {order?.product.name}</Text>
      </View>

      <View style={{flexDirection: "row"}}>
        <Button
          title={"Recusar"}
          style={[styles.button, {marginRight: 10, backgroundColor: Theme.nextButton.colors.canceled}]}
          textColor={{color: Theme.textColor.white}}
          onPress={() => onDeclineOrder()}
        />
        <Button
          title={"Candidatar-se"}
          style={styles.button}
          textColor={{color: Theme.textColor.dark}}
          onPress={() => onConfirmOrder()}
        />
      </View>
      <View>
        <OrderConfirmModal onPress={handleConfirmOrder} isModalVisible={visible}/>
      </View>
    </>
  );
};

const styles = StyleSheet.create({
  button: {flex: 1, backgroundColor: Theme.nextButton.colors.confirmed, marginVertical: 16},
  addressText: {fontSize: Theme.textSize.secondary, color: Theme.textColor.yellow, marginBottom: 2},
  profileText: {fontSize: Theme.textSize.secondary, color: Theme.textColor.secondary, marginBottom: 5},
  titleText: {fontSize: Theme.textSize.primary, color: Theme.textColor.primary}
});
