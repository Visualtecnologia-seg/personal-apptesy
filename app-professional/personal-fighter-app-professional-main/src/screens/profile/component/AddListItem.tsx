import React from "react";
import {StyleSheet, View} from "react-native";
import {Theme} from "../../../theme";
import {Feather} from "@expo/vector-icons";
import {TouchableOpacity} from "react-native-gesture-handler";

interface Props {
  onPress?: any;
}

export const AddListItem: React.FC<Props> = props => {

  return (
    <View style={styles.itemRowContainer}>
      <TouchableOpacity onPress={props.onPress}>
        <Feather
          name="plus-circle"
          size={24}
          color={Theme.iconColor}
        />
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  itemRowContainer: {
    backgroundColor: Theme.card.backgroundColor,
    paddingHorizontal: 16,
    height: 40,
    shadowColor: Theme.shadowColor,
    shadowOffset: {
      width: 0,
      height: 2
    },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 1,
    marginVertical: 5,
    justifyContent: "center",
    alignItems: "flex-end",
    borderRadius: 8
  }
});
