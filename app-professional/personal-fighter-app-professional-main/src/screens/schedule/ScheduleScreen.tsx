import React, {useEffect, useState} from "react";
import {RefreshControl, ScrollView, StyleSheet, Text, View} from "react-native";
import {GREY, SCREEN_HEIGHT, Theme, WHITE3} from "../../theme";
import {useAuth} from "../../context/AuthContext";
import {Loading} from "../../components/general/loading";
import {ButtonGroup} from "react-native-elements";
import {Button, Divider, ScheduleTimes} from "../../components";
import {getProfessionalAgenda, updateAgenda} from "../../services/AgendaService";
import {useIsFocused, useNavigation} from "@react-navigation/native";

type TProps = {};

export const ScheduleScreen: React.FC<TProps> = props => {
  /* Default */
  const isFocused = useIsFocused();
  const {token, user} = useAuth();
  const navigation = useNavigation();
  const [loading, setLoading] = useState(true);
  const [scheduleLoading, setScheduleLoading] = useState(false);
  const [changed, setChanged] = useState(false);
  const buttons = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];
  const [index, setIndex] = useState(0);
  const [agendas, setAgendas] = useState<any[]>(null);
  const [selectedDate, setSelectedDate] = useState(new Date());

  useEffect(() => {
    if (isFocused) {
      loadAgenda();
    }
  }, [isFocused]);

  function loadAgenda() {
    getProfessionalAgenda(user.id, selectedDate).then(res => {
      if (res.error) {
        navigation.goBack();
        return;
      }
      setAgendas(res.data);
      setLoading(false);
    });
  }

  function handleStatus(i) {
    setScheduleLoading(true);
    handleSave();
    setIndex(i);
  }

  function onTimeSelected(time) {
    setChanged(!changed);
    let schedule = JSON.parse(agendas[index]?.schedule);
    schedule?.forEach(day => {
      if (Object.keys(day).toString() === time) {
        let check = Boolean(Object.values(day)[0]);
        day[time] = !check;
      }
    });
    agendas[index].schedule = JSON.stringify(schedule);
  }

  function handleSave() {
    setLoading(true);
    updateAgenda(agendas[index]).then(() => {
      setLoading(false);
      setScheduleLoading(false);
    });
  }

  if (loading) {
    return (<Loading/>);
  }

  return (
    <ScrollView
      contentContainerStyle={styles.scrollview}
      showsVerticalScrollIndicator={false}
      refreshControl={
        <RefreshControl refreshing={loading} onRefresh={() => loadAgenda()}/>
      }
    >
      <ButtonGroup
        onPress={i => handleStatus(i)}
        selectedIndex={index}
        buttons={buttons}
        containerStyle={styles.container}
        buttonStyle={styles.unselectedButton}
        textStyle={styles.unselectedText}
        selectedButtonStyle={styles.selectedButton}
        selectedTextStyle={styles.selectedText}
        innerBorderStyle={{color: GREY}}
      />
      <View>
        <Divider style={{marginTop: 20}}/>
        <Text style={styles.title}>Horários em vermelho indisponíveis</Text>
        <Divider/>
        {scheduleLoading && <Loading/>}
        <ScheduleTimes
          onTimeSelected={onTimeSelected}
          agendas={agendas}
          selectedDate={selectedDate}
          dayOfWeek={index}
          changed={changed}/>
        <Button
          title={"Salvar"}
          style={{marginHorizontal: 16, backgroundColor: Theme.nextButton.colors.primary, marginTop: 20}}
          textColor={{color: Theme.textColor.dark}}
          onPress={() => handleSave()}/>
      </View>
    </ScrollView>
  )
    ;
};

const styles = StyleSheet.create({
  scrollview: {
    flex: 1,
    backgroundColor: Theme.scrollViewBackgroundColor
  },
  container: {
    height: 35,
    borderRadius: 8,
    borderColor: Theme.nextButton.colors.border,
    marginTop: 16,
    marginHorizontal: 16
  },
  unselectedButton: {
    backgroundColor: Theme.nextButton.colors.unselected
  },
  selectedButton: {
    backgroundColor: Theme.nextButton.colors.selected
  },
  selectedText: {
    color: Theme.textColor.yellow,
    fontSize: Theme.textSize.primary
  },
  unselectedText: {
    color: Theme.textColor.primary,
    fontSize: Theme.textSize.primary
  },
  flatList: {
    marginVertical: 5
  },
  mainView: {
    flex: 1,
    height: SCREEN_HEIGHT / 2,
    justifyContent: "center",
    alignItems: "center"
  },
  sectionTitle: {
    marginHorizontal: 16,
    textAlign: "center",
    fontSize: Theme.textSize.section * 1.2,
    color: WHITE3,
    marginTop: 30
  },
  title: {
    fontSize: Theme.textSize.headerTitle,
    color: Theme.textColor.yellow,
    textAlign: "center",
    marginVertical: 5,
    marginHorizontal: 16
  }
});
