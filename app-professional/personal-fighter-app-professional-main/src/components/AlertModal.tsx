import React from 'react';
import {Modal, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {DARKGREY, LIGHTGREY, SCREEN_WIDTH, Theme, YELLOW} from "../theme";
import {hex2rgba} from "../utils/Hex2Rgba";
import {Divider} from "./general/divider";

type Props = {
  onPress: () => any;
  isModalVisible: boolean;
  title: string;
  text: string;
};

const AlertModal: React.FC<Props> = props => (
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
            <Text style={styles.title}>{props.title}</Text>
          </View>
          <Divider style={{marginBottom: 10}}/>
          <View style={styles.viewText}>
            <Text style={styles.subtitle}>{props.text}</Text>
          </View>
          <Divider style={{marginVertical: 10}}/>
          <View style={styles.viewButton}>
            <TouchableOpacity
              style={styles.button}
              onPress={props.onPress}
            >
              <Text style={styles.textButton}>OK</Text>
            </TouchableOpacity>
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
    backgroundColor: hex2rgba(LIGHTGREY, 96),
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
  button: {
    borderRadius: 8,
    padding: 6,
    elevation: 2,
    backgroundColor: YELLOW,
    width: 100,
  },
  viewButton: {
    justifyContent: "center",
    alignItems: "center",
    marginTop: 10, marginBottom: 10
  },
  viewText: {
    justifyContent: "center",
    alignItems: "center",
  },
  textButton: {
    color: Theme.textColor.dark,
    fontWeight: "bold",
    textAlign: "center",
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
    textAlign: "center",
  },
});

export default AlertModal;
