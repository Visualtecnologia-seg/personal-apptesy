import React from "react";
import {StyleSheet, Text, View} from "react-native";
import {Theme, YELLOW} from "../../../theme";
import {TouchableOpacity} from "react-native-gesture-handler";
import {Checkbox} from "react-native-paper";

type TProps = {
  title?: string;
  index?: number;
  checked?: boolean;
  onPress?: () => void;
};

export const ProductListItem: React.FC<TProps> = props => {
  return (
    <View style={[styles.itemRowContainer]}>
      <TouchableOpacity onPress={() => props.onPress()}>
        <View style={{flexDirection: "row"}}>
          <View style={styles.centerContainer}>
            <Text style={styles.title}>{props.title}</Text>
          </View>
          <View style={styles.rightContainer}>
            <Checkbox.Android
              status={props.checked ? "checked" : "unchecked"}
              onPress={() => {
              }}
              color={YELLOW}
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
    paddingHorizontal: 16,
    height: 55,
    flex: 1,
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
    color: Theme.textColor.white,
    textAlign: "left"
  }
});
