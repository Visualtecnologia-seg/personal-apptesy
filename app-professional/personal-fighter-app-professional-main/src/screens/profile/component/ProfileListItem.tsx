import React from "react";
import {StyleSheet, Text, View} from "react-native";
import {Theme} from "../../../theme";
import {Feather, MaterialCommunityIcons} from "@expo/vector-icons";
import {TouchableOpacity} from "react-native-gesture-handler";

type TProps = {
  icon: string;
  title: string;
  navigation?: () => void;
};

export const ProfileListItem: React.FC<TProps> = props => {

  return (
    <View style={styles.itemRowContainer}>
      <TouchableOpacity onPress={props.navigation}>
        <View style={{flexDirection: "row"}}>
          <View style={styles.leftContainer}>
            {props.icon !== "boxing-glove" &&
            // @ts-ignore
            <Feather name={props.icon} size={24} color={Theme.iconColor} style={{alignSelf: "center"}}/>}
            {props.icon === "boxing-glove" &&
            <MaterialCommunityIcons
              name={props.icon} size={24} color={Theme.iconColor}
              style={{alignSelf: "center", transform: [{rotateY: "180deg"}]}}/>}
          </View>
          <View style={styles.centerContainer}>
            <Text style={styles.title}>{props.title}</Text>
          </View>
          <View style={styles.rightContainer}>
            <Feather
              name="chevrons-right"
              size={24}
              color={Theme.iconColor}
              style={{alignSelf: "center"}}
            />
          </View>
        </View>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  itemRowContainer: {
    backgroundColor: Theme.card.backgroundColor,
    marginHorizontal: 16,
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
    color: Theme.textColor.secondary,
    textAlign: "left"
  }
});
