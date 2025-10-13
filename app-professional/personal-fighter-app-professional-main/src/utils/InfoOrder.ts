export const infoOrder = (numberOfCustomers: number) => {
  const getPrice = () => {
    switch (numberOfCustomers) {
      case 1: {
        return 70;
      }
      case 2: {
        return 100;
      }
      case 3: {
        return 150;
      }
      case 4: {
        return 200;
      }
      case 5: {
        return 250;
      }
      default: {
        return 250;
      }
    }
  }

  if (numberOfCustomers === 1) {
    return {icon: "user", group: "Aula particular", price: getPrice()};
  } else if (numberOfCustomers === 2) {
    return {icon: "user-plus", group: "Grupos pequenos", price: getPrice()};
  } else if (numberOfCustomers === 3) {
    return {icon: "user-plus", group: "Grupos pequenos", price: getPrice()};
  } else if (numberOfCustomers === 4) {
    return {icon: "user-plus", group: "Grupos pequenos", price: getPrice()};
  } else {
    return {icon: "users", group: "Grupos grandes", price: getPrice()};
  }

};