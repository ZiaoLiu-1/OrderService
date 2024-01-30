import requests


def make_post_request(url, data):
  try:
    headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
    response = requests.post(url, json=data, headers=headers)
    if response.status_code == 200:
      print(f"POST request did work: {response.status_code}")  
      print("Response: ", response.text)
    
    else:
      print(f"POST request did not work: {response.status_code}")  
      print("Response: ", response.text)
  except Exception as e:
    print(e)
if __name__ == "__main__":
  url = 'http://127.0.0.1:14000/product'
  url1 = 'http://127.0.0.1:14000/order'

  data0 = {"command": "update", "id": 2, 'name' : "hello123" }
  data1 = {"command": "restart"}
  data2 = {"command": "shutdown"}
  data3 = {"command": "create", "id" : 1, "username" : 'hello', "email" : "2333@qqqq.com", "password": "alskdj"}
  data4 = {"command": "update", "id": 1, 'username' : "hello111" }
  data5 = {"command": "place order", "product_id": 2, 'user_id' : 1, "quantity": 1 }
  data6 = {"command": "delete", "id" : 101, "name" : 'PhoneX',  "price": 899.99, "quantity": 60}
  make_post_request(url1, data1)
  #make_post_request(url1, data1)