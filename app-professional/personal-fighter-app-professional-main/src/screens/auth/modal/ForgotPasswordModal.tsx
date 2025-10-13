import React, {useState} from "react";
import {Modal, StyleSheet, Text, TextInput, TouchableOpacity, View} from "react-native";
import {LIGHTGREY, RED, SCREEN_WIDTH, Theme, WHITE2, YELLOW} from "../../../theme";
import {hex2rgba} from "../../../utils/Hex2Rgba";
import {forgotPassword} from "../../../services/AuthService";

type Props = {
  handleForgotModal: (visible) => any;
  isModalVisible: boolean;
};

const ForgotPasswordModal: React.FC<Props> = props => {
  const [email, setEmail] = useState<string>("");

  function handleConfirm() {
    forgotPassword({username: email})
      .then(res => {
        props.handleForgotModal(false);
      });
  }

  return (
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
              <Text style={styles.title}>Recuperação de senha</Text>
            </View>

            <View style={{marginBottom: 5}}>
              <View style={styles.inputContainer}>
                <TextInput
                  keyboardAppearance="dark"
                  placeholderTextColor="#666360"
                  placeholder="E-mail de cadastro..."
                  selectionColor={YELLOW}
                  style={styles.inputText}
                  value={email}
                  onChangeText={(text) => {
                    setEmail(text);
                  }}
                />
              </View>
            </View>
            {/*<Divider style={{marginVertical: 10}}/>*/}
            <View style={{flexDirection: "row", justifyContent: "center", marginTop: 10, marginBottom: 10}}>
              <View style={[styles.viewButton, {marginRight: 10}]}>
                <TouchableOpacity
                  style={styles.cancelButton}
                  onPress={() => props.handleForgotModal(false)}
                >
                  <Text style={[styles.nextText, {color: WHITE2}]}>CANCELAR</Text>
                </TouchableOpacity>
              </View>
              <View style={styles.viewButton}>
                <TouchableOpacity
                  style={styles.confirmButton}
                  onPress={() => handleConfirm()}
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
};


const styles = StyleSheet.create({
  centeredView: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: hex2rgba(LIGHTGREY, 96)
  },
  modalView: {
    backgroundColor: Theme.scrollViewBackgroundColor,
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
    justifyContent: "center",
    alignItems: "center",
    marginTop: 10, marginBottom: 10
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
    marginBottom: 16
  },
  subtitle: {
    color: Theme.textColor.secondary,
    fontSize: Theme.textSize.primary,
    textAlign: "center"
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

export default ForgotPasswordModal;
