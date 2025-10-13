import React, {useEffect, useState} from "react";
import {ActivityIndicator, FlatList, RefreshControl, ScrollView, StyleSheet, View} from "react-native";
import {GREY, SCREEN_HEIGHT, Theme, WHITE3, YELLOW} from "../../theme";
import {ButtonGroup} from "react-native-elements";
import {Loading} from "../../components/general/loading";
import {cancelOrder, getOrdersByStatus} from "../../services/OrderService";
import {OrderModel} from "../../model";
import OrderCancelModal from "./modal/OrderCancelModal";
import {useAuth} from "../../context/AuthContext";
import {useIsFocused} from "@react-navigation/native";
import ListEmptyComponent from "../../components/ListEmptyComponent";
import {ReqParamsModel} from "../../model/ReqParamsModel";
import {OrderItem} from "./component/OrderItem";
import {usePushNotification} from "../../context/PushNotificationContext";

export const OrderScreen: React.FC = props => {
  /* Default */
  const isFocused = useIsFocused();
  const {user} = useAuth();
  const [loading, setLoading] = useState(true);
  const [visible, setVisible] = useState(false);
  const {pushNotification} = usePushNotification();
  /* Pagination */
  const [loadMore, setLoadMore] = useState<boolean>(true);
  const [reqParams, setReqParams] = useState<ReqParamsModel>(undefined);
  /* Tabs */
  const buttons = ["Confirmadas", "Realizadas", "Canceladas"];
  const [index, setIndex] = useState(0);
  const [status, setStatus] = useState("CONFIRMED");
  /* Orders*/
  const [orders, setOrders] = useState<OrderModel[]>(null);
  const [order, setOrder] = useState<OrderModel>(null);

  useEffect(() => {
    setLoading(true);
    if (isFocused) {
      onRefresh();
    }
  }, [isFocused, status, pushNotification]);

  function changeTab(index) {
    setIndex(index);
    // if (index === 0) setStatus("OPEN");
    if (index === 0) setStatus("CONFIRMED");
    if (index === 1) setStatus("DONE");
    if (index === 2) setStatus("CANCELLED");
  }

  const loadOrders = (status?: string, firstLoad?: boolean, params?: any) => {
    if (firstLoad) {
      setLoading(true);
    }
    getOrdersByStatus(status, user.id, params).then(res => {
      if (res.error) {
        setLoading(false);
        setLoadMore(false);
      } else {
        if (firstLoad) {
          setOrders(res.data.content);
          res.data.content.length === 0 && setLoadMore(false);
          setLoadMore(res?.totalPages > params.page);
        } else {
          setOrders([...orders, ...res.data.content]);
          setLoadMore(res?.totalPages > params.page);
        }
        setLoading(false);
      }
    });
  };

  function onCancelOrder(order) {
    setVisible(true);
    setOrder(order);
  }

  function handleCancelOrder(confirm) {
    setLoading(true);
    if (confirm) {
      cancelOrder(order, user.id).then(res => {
        setVisible(false);
        setLoading(false);
        onRefresh();
      });
    } else {
      setVisible(false);
      setLoading(false);
    }
  }

  function onEndReached() {
    if (loadMore) {
      const params = {page: reqParams.page + 1, sort: "date,asc", active: true, size: 5};
      setReqParams(params);
      loadOrders(status, false, params);
    }
  }

  function onRefresh() {
    setLoading(true);
    setOrders(undefined);
    const params = {page: 0, sort: "date,asc", active: true, size: 5};
    setReqParams(params);
    loadOrders(status, true, params);
  }

  return (
    <ScrollView
      contentContainerStyle={styles.scrollview}
    >
      <ButtonGroup
        onPress={i => changeTab(i)}
        selectedIndex={index}
        buttons={buttons}
        containerStyle={styles.container}
        buttonStyle={styles.unselectedButton}
        textStyle={styles.unselectedText}
        selectedButtonStyle={styles.selectedButton}
        selectedTextStyle={styles.selectedText}
        innerBorderStyle={{color: GREY}}
      />
      {loading && <Loading/>}
      {!loading && <FlatList
        data={orders}
        scrollEnabled={true}
        keyExtractor={item => item.id.toString()}
        renderItem={({item}) => (
          <OrderItem
            order={item}
            loading={loading}
            onRefresh={() => onRefresh()}
            onCancelOrder={onCancelOrder}/>
        )}
        style={styles.flatList}
        ListEmptyComponent={<ListEmptyComponent icon={"calendar"} text={"Sem aulas no momento."}/>}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={onRefresh}/>}
        onEndReached={onEndReached}
        onEndReachedThreshold={0.1}
        ListFooterComponent={() => {
          return loadMore && <ActivityIndicator size="small" color={YELLOW} style={{marginVertical: 20}}/>;
        }}
      />}
      <View>
        <OrderCancelModal onPress={handleCancelOrder} isModalVisible={visible} status={order?.status}/>
      </View>

    </ScrollView>
  );
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
  text: {
    marginHorizontal: 16,
    textAlign: "center",
    fontSize: Theme.textSize.section * 1.2,
    color: WHITE3,
    marginTop: 30
  }
});
