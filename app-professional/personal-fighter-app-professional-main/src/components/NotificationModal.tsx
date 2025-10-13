import React from "react";
import {Modal, StyleSheet, Text, TouchableOpacity, View} from "react-native";
import {DARKGREY, LIGHTGREY, RED, SCREEN_WIDTH, Theme, YELLOW} from "../theme";
import {hex2rgba} from "../utils/Hex2Rgba";

export interface NotificationModel {
  title?: string,
  text: string,
  visible: boolean,
  showClose?: boolean,
  onClose?: any,
  showConfirm?: boolean,
  onConfirm?: any
}

interface Props {
  notification?: NotificationModel;
  setVisible?: (visible) => void;
  onPress?: () => any;
  showClose?: boolean;
}

const NotificationModal: React.FC<Props> = props => {
  const notification = props.notification;

  return (<View style={styles.centeredView}>
    <Modal
      animationType="fade"
      transparent={true}
      visible={notification.visible}
      statusBarTranslucent={true}
    >
      <View style={styles.centeredView}>
        <View style={styles.modalView}>
          <View style={styles.viewText}>
            <Text style={styles.title}>{notification.title ? notification.title : "Atenção!"}</Text>
          </View>
          <View style={styles.viewTitle}>
            <Text style={styles.text}>{notification.text}</Text>
          </View>

          <View style={styles.viewButton}>
            {props.showClose && <TouchableOpacity
              style={styles.closeButton}
              onPress={() => props.setVisible({visible: false} as NotificationModel)}
            >
              <Text style={styles.closeText}>Fechar</Text>
            </TouchableOpacity>}
            {notification.onConfirm && <TouchableOpacity
              style={styles.nextButton}
              onPress={notification.onConfirm}
            >
              <Text style={styles.nextText}>OK</Text>
            </TouchableOpacity>}
          </View>
        </View>
      </View>
    </Modal>
  </View>);
};

const styles = StyleSheet.create({
  centeredView: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: hex2rgba(LIGHTGREY, 96),
  },
  modalView: {
    backgroundColor: DARKGREY,
    borderRadius: 8,
    padding: 16,
    shadowColor: Theme.shadowColor,
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 1,
    width: SCREEN_WIDTH - 120,
  },
  nextButton: {
    borderRadius: 8,
    padding: 6,
    elevation: 2,
    backgroundColor: YELLOW,
    width: 100,
  },
  nextText: {
    color: Theme.textColor.dark,
    fontWeight: "bold",
    textAlign: "center",
  },
  closeButton: {
    borderRadius: 8,
    padding: 6,
    elevation: 2,
    backgroundColor: RED,
    width: 100,
    marginRight: 5,
  },
  closeText: {
    color: Theme.textColor.secondary,
    fontWeight: "bold",
    textAlign: "center",
  },
  viewButton: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    marginTop: 10,
    marginBottom: 10,
  },
  viewText: {
    justifyContent: "center",
    alignItems: "center",
  },
  text: {
    color: Theme.textColor.secondary,
    fontSize: Theme.textSize.primary,
    textAlign: "center",
  },
  viewTitle: {
    justifyContent: "center",
    alignItems: "center",
    marginVertical: 10,
  },
  title: {
    color: Theme.textColor.primary,
    fontSize: Theme.textSize.section,
    textAlign: "center",
    marginBottom: 10,
  },
});

export default NotificationModal;
