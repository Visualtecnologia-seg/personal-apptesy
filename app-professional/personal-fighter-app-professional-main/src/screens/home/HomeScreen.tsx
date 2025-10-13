import React, {useEffect, useState} from "react";
import {ActivityIndicator, FlatList, RefreshControl, ScrollView, StyleSheet} from "react-native";
import {SCREEN_HEIGHT, Theme, WHITE3, YELLOW} from "../../theme";
import {Loading} from "../../components/general/loading";
import {getOrdersByProfessional} from "../../services/OrderService";
import {OrderModel} from "../../model";
import {useAuth} from "../../context/AuthContext";
import {OrderItem} from "../order/component/OrderItem";
import {useIsFocused} from "@react-navigation/native";
import {ReqParamsModel} from "../../model/ReqParamsModel";
import ListEmptyComponent from "../../components/ListEmptyComponent";
import {usePushNotification} from "../../context/PushNotificationContext";

type TProps = {};

export const HomeScreen: React.FC<TProps> = props => {
  /* Default */
  const isFocused = useIsFocused();
  const {user, signOut} = useAuth();
  const [loading, setLoading] = useState(false);
  const {pushNotification} = usePushNotification();
  /* Pagination */
  const [loadMore, setLoadMore] = useState<boolean>(true);
  const [reqParams, setReqParams] = useState<ReqParamsModel>(undefined);
  /* Orders */
  const [orders, setOrders] = useState<OrderModel[]>([]);

  useEffect(() => {
    if (isFocused && user?.id) {
      onRefresh();
    }
  }, [isFocused, pushNotification, user]);

  const loadOrders = (firstLoad?: boolean, params?: any) => {
    getOrdersByProfessional(user.id, params).then(res => {
      if (res.error) {
        setLoading(false);
        setLoadMore(false);
      } else {
        if (firstLoad) {
          setOrders(res.data.content);
          res.data.content.length === 0 && setLoadMore(false);
          setLoadMore(res?.totalPages > params.page);
          setLoading(false);
        } else {
          setOrders([...orders, ...res.data.content]);
          setLoadMore(res?.totalPages > params.page);
          setLoading(false);
        }
      }
    });
  };

  function onEndReached() {
    if (loadMore) {
      const params = {page: reqParams.page + 1, sort: "date,desc", active: true, size: 10};
      setReqParams(params);
      loadOrders(false, params);
    }
  }

  function onRefresh() {
    setLoading(true);
    setOrders(undefined);
    const params = {page: 0, sort: "date,desc", active: true, size: 10};
    setReqParams(params);
    loadOrders(true, params);
  }

  if (loading) {
    return (<Loading/>);
  }

  return (
    <ScrollView
      contentContainerStyle={styles.scrollview}
    >
      <FlatList
        data={orders}
        scrollEnabled={true}
        keyExtractor={item => item.id.toString()}
        renderItem={({item}) => (
          <OrderItem order={item} loading={loading} onRefresh={() => onRefresh()}/>
        )}
        style={styles.flatList}
        ListEmptyComponent={<ListEmptyComponent icon={"calendar"} text={"Sem aulas no momento."}/>}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={onRefresh}/>}
        onEndReached={onEndReached}
        onEndReachedThreshold={0.1}
        ListFooterComponent={() => {
          return loadMore && <ActivityIndicator size="small" color={YELLOW} style={{marginVertical: 20}}/>;
        }}
      />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  scrollview: {
    flex: 1,
    backgroundColor: Theme.scrollViewBackgroundColor,
    paddingTop: 16
  },
  container: {
    height: 35,
    borderRadius: 8,
    borderColor: Theme.nextButton.colors.border,
    marginTop: 16,
    marginHorizontal: 16
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
  datepicker: {
    marginVertical: 2,
    marginHorizontal: 16
  },
  calendar: {
    height: 60,
    marginHorizontal: 4
  },
  title: {
    fontSize: Theme.textSize.headerTitle,
    color: Theme.textColor.primary,
    textAlign: "center",
    marginVertical: 5,
    marginHorizontal: 16
  }
});
