import React from "react";
import {StyleSheet, Text, View} from "react-native";
import {Theme} from "../../../theme";
import {Feather} from "@expo/vector-icons";
import {TouchableOpacity} from "react-native-gesture-handler";

type TProps = {
  place?: string;
  text?: string;
  onPress?: any
};

export const ResumeListItem: React.FC<TProps> = props => {

  return (
    <View style={styles.itemRowContainer}>
      <View style={{flexDirection: "row"}}>
        <View style={styles.leftContainer}>
          <Text style={styles.title}>{props.place}</Text>
        </View>
        <View style={styles.centerContainer}>
          <Text style={styles.title}>{props.text}</Text>
        </View>
        <TouchableOpacity style={styles.rightContainer} onPress={props.onPress}>
          <Feather
            name="trash"
            size={24}
            color={Theme.iconColor}
            style={{marginTop: 5}}
          />
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  itemRowContainer: {
    backgroundColor: Theme.card.backgroundColor,
    paddingHorizontal: 16,
    height: 55,
    shadowColor: Theme.shadowColor,
    shadowOffset: {
      width: 0,
      height: 2
    },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 1,
    marginBottom: 12,
    justifyContent: "center",
    borderRadius: 8
  },
  leftContainer: {
    alignSelf: "center"
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
    color: Theme.textColor.secondary,
    textAlign: "left"
  }
});
