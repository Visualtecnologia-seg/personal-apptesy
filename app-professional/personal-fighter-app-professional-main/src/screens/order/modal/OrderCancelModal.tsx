import React from "react";
import {Modal, StyleSheet, Text, TouchableOpacity, View} from "react-native";
import {DARKGREY, LIGHTGREY, RED, SCREEN_WIDTH, Theme, WHITE2, YELLOW} from "../../../theme";
import {hex2rgba} from "../../../utils/Hex2Rgba";

type Props = {
  onPress: (confirm) => any;
  isModalVisible: boolean;
  status: string;
};

const OrderCancelModal: React.FC<Props> = props => (
  <View style={styles.centeredView}>
    <Modal
      animationType="fade"
      transparent={true}
      visible={props.isModalVisible}
      statusBarTranslucent={true}
    >
      <View style={styles.centeredView}>
        <View style={styles.modalView}>
          <View style={styles.viewText}>
            <Text style={styles.title}>Cancelamento</Text>
          </View>
          <View style={styles.viewText}>
            <Text style={styles.subtitle}>Após essa operação o agendamento será cencelado e será cobrada uma taxa de
              cancelamento no valor de {props.status === "OPEN" ? "R$ 0,70" : "R$ 3,80"}. Deseja continuar?</Text>
          </View>
          <View style={{flexDirection: "row", justifyContent: "center", marginTop: 10, marginBottom: 10}}>
            <View style={[styles.viewButton, {marginRight: 10}]}>
              <TouchableOpacity
                style={styles.cancelButton}
                onPress={() => props.onPress(false)}
              >
                <Text style={[styles.nextText, {color: WHITE2}]}>CANCELAR</Text>
              </TouchableOpacity>
            </View>
            <View style={styles.viewButton}>
              <TouchableOpacity
                style={styles.confirmButton}
                onPress={() => props.onPress(true)}
              >
                <Text style={styles.nextText}>CONFIRMAR</Text>
              </TouchableOpacity>
            </View>
          </View>

        </View>
      </View>
    </Modal>
  </View>
);

const styles = StyleSheet.create({
  centeredView: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: hex2rgba(LIGHTGREY, 96)
  },
  modalView: {
    backgroundColor: DARKGREY,
    borderRadius: 8,
    padding: 16,
    shadowColor: Theme.shadowColor,
    shadowOffset: {
      width: 0,
      height: 2
    },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 1,
    width: SCREEN_WIDTH - 120
  },
  confirmButton: {
    borderRadius: 8,
    padding: 6,
    elevation: 2,
    backgroundColor: YELLOW,
    width: 100
  },
  cancelButton: {
    borderRadius: 8,
    padding: 6,
    elevation: 2,
    backgroundColor: RED,
    width: 100
  },
  viewButton: {
    justifyContent: "flex-end",
    alignItems: "flex-end",
    marginTop: 10
  },
  viewText: {
    justifyContent: "center",
    alignItems: "center"
  },
  nextText: {
    color: Theme.textColor.dark,
    fontWeight: "bold",
    textAlign: "center"
  },
  title: {
    color: Theme.textColor.primary,
    fontSize: Theme.textSize.section,
    textAlign: "center",
    marginBottom: 10
  },
  subtitle: {
    color: Theme.textColor.secondary,
    fontSize: Theme.textSize.primary,
    textAlign: "center"
  }
});

export default OrderCancelModal;
