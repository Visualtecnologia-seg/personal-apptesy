import React, {useState} from "react";
import {Modal, StyleSheet, Text, TextInput, TouchableOpacity, View} from "react-native";
import {DARKGREY, LIGHTGREY, RED, SCREEN_WIDTH, Theme, YELLOW} from "../../../theme";
import {hex2rgba} from "../../../utils/Hex2Rgba";


export interface NotificationModel {
  visible: boolean,
  onClose?: any,
  onConfirm?: any
}

interface Props {
  visible?: boolean;
  setVisible?: (visible) => void;
  onConfirm?: any;
  onCancel?: any;
}

const ResumeModal: React.FC<Props> = props => {

  const [place, setPlace] = useState<string>("");
  const [league, setLeague] = useState<string>("");

  return (<View style={styles.centeredView}>
    <Modal
      animationType="fade"
      transparent={true}
      visible={props.visible}
      statusBarTranslucent={true}
    >
      <View style={styles.centeredView}>
        <View style={styles.modalView}>
          <View style={styles.viewText}>
            <Text style={styles.title}>Adicionar título</Text>
          </View>

          <View style={{marginBottom: 5}}>
            <Text style={styles.subTitle}>Colocação</Text>
            <View style={styles.inputContainer}>
              <TextInput
                keyboardAppearance="dark"
                placeholderTextColor="#666360"
                placeholder="Ex: Campeão, Segundo etc"
                maxLength={8}
                selectionColor={YELLOW}
                style={styles.inputText}
                value={place}
                onChangeText={(text) => {
                  setPlace(text);
                }}
              />
            </View>
          </View>
          <View style={{marginBottom: 5}}>
            <Text style={styles.subTitle}>Campeonato</Text>
            <View style={styles.inputContainer}>
              <TextInput
                keyboardAppearance="dark"
                placeholderTextColor="#666360"
                placeholder="Ex: Campeonato Brasileiro"
                maxLength={32}
                selectionColor={YELLOW}
                style={styles.inputText}
                value={league}
                onChangeText={(text) => {
                  setLeague(text);
                }}
              />
            </View>
          </View>

          <View style={styles.viewButton}>
            <TouchableOpacity
              style={styles.closeButton}
              onPress={props.onCancel}
            >
              <Text style={styles.closeText}>Cancelar</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.nextButton}
              onPress={() => props.onConfirm(place, league)}
            >
              <Text style={styles.nextText}>Confirmar</Text>
            </TouchableOpacity>
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
  nextButton: {
    borderRadius: 8,
    padding: 6,
    elevation: 2,
    backgroundColor: YELLOW,
    width: 100
  },
  nextText: {
    color: Theme.textColor.dark,
    fontWeight: "bold",
    textAlign: "center"
  },
  closeButton: {
    borderRadius: 8,
    padding: 6,
    elevation: 2,
    backgroundColor: RED,
    width: 100,
    marginRight: 5
  },
  closeText: {
    color: Theme.textColor.secondary,
    fontWeight: "bold",
    textAlign: "center"
  },
  viewButton: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    marginTop: 10,
    marginBottom: 10
  },
  viewText: {
    justifyContent: "center",
    alignItems: "center"
  },
  text: {
    color: Theme.textColor.secondary,
    fontSize: Theme.textSize.primary,
    textAlign: "center"
  },
  viewTitle: {
    justifyContent: "center",
    alignItems: "center",
    marginVertical: 10
  },
  title: {
    color: Theme.textColor.primary,
    fontSize: Theme.textSize.section,
    textAlign: "center",
    marginBottom: 10
  },
  inputContainer: {
    flexDirection: "row",
    paddingHorizontal: 16,
    height: 50,
    width: "100%",
    backgroundColor: Theme.card.backgroundColor,
    borderRadius: 8
  },
  inputText: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary
  },
  inputIcon: {
    alignSelf: "center",
    marginRight: 16
  },
  subTitle: {
    color: Theme.textColor.secondary,
    fontSize: Theme.textSize.secondary,
    marginBottom: 5,
    marginLeft: 2
  }
});

export default ResumeModal;
