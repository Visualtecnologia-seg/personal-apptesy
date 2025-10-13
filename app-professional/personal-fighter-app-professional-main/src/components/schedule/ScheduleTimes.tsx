import React, {useEffect, useState} from "react";
import {FlatList, StyleSheet, Text, View} from "react-native";
import {GREEN, SCREEN_WIDTH, Theme, WHITE2, WHITE3} from "../../theme";
import {TouchableOpacity} from "react-native-gesture-handler";
import {ScheduleTimeModel} from "../../model";
import moment from "moment";

type TProps = {
  onTimeSelected: (time) => void;
  loading?: boolean;
  agendas?: any;
  selectedDate?: any
  dayOfWeek?: number;
  changed?: boolean;
};

export const ScheduleTimes: React.FC<TProps> = props => {
  const [times, setTimes] = useState<ScheduleTimeModel[]>()

  const getColors = (item) => {
    let backgroundColor = Theme.nextButton.colors.canceled;
    let color = Theme.textColor.secondary;
    if (item.available === true) {
      backgroundColor = Theme.nextButton.colors.selected
      color = Theme.textColor.primary
    }

    return {backgroundColor: backgroundColor, color: color}
  }

  const availableTimes = () => {
    return props.agendas[props.dayOfWeek].schedule;
  }

  useEffect(() => {
    let times = [];
    let available = JSON.parse("[" + availableTimes() + "]");
    let today = moment(new Date()).format("YYYY-MM-DD");
    let selected = moment(props.selectedDate).format("YYYY-MM-DD");
    available[0].forEach(item => {
      let obj: ScheduleTimeModel = {};
      let available = selected >= today ? Object.values(item)[0] as boolean : false
      obj.time = Object.keys(item).toString();
      obj.available = available;
      times.push(obj)
    })
    setTimes(times);
  }, [props.selectedDate, props.agendas, props.dayOfWeek, props.changed])

  return (
    <View style={styles.itemContainer}>
      <FlatList
        data={times}
        numColumns={6}
        scrollEnabled={false}
        style={styles.flatListStyle}
        columnWrapperStyle={styles.columnWrapperStyle}
        keyExtractor={(item, index) => `key${index}ForTime`}
        renderItem={({item}) => (
          <TouchableOpacity
            style={[
              styles.timeContainer, {
                backgroundColor: getColors(item).backgroundColor
              }
            ]}
            onPress={() => props.onTimeSelected(item?.time)}
          >
            <Text
              style={{color: getColors(item).color}}>{item.available ? item?.time : "X"}</Text>
          </TouchableOpacity>
        )}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Theme.scrollViewBackgroundColor
  },
  itemContainer: {
    paddingVertical: 0
  },
  flatListStyle: {
    marginTop: 16,
    marginBottom: 4
  },
  columnWrapperStyle: {
    marginHorizontal: 12
  },
  timeContainer: {
    height: 32,
    width: SCREEN_WIDTH / 6 - 4 - 8,
    margin: 4,
    borderRadius: 4,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 0.3,
  },
});
