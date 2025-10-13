// @ts-nocheck
import {StyleSheet, Text, View} from "react-native";
import {Feather} from "@expo/vector-icons";
import {SCREEN_HEIGHT, Theme, WHITE3} from "../theme";
import React from "react";

interface Props {
  text: string;
  icon: string;
}

const ListEmptyComponent: React.FC<Props> = props => {
  return (
    <View style={styles.mainView}>
      <Feather name={props.icon} size={64} color={WHITE3}/>
      <Text style={styles.text}>{props.text}</Text>
    </View>);
};

const styles = StyleSheet.create({
  mainView: {
    flex: 1,
    height: SCREEN_HEIGHT / 1.5,
    justifyContent: "center",
    alignItems: "center",
  },
  text: {
    marginHorizontal: 16,
    textAlign: "center",
    fontSize: Theme.textSize.section * 1.2,
    color: WHITE3,
    marginTop: 10,
  },
});
export default ListEmptyComponent;
