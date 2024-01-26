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
  url = 'http://127.0.0.1:15000/product'

  data = {"command": "update","id": 3, "price": 1000, "quantity": 100} 

  make_post_request(url, data)
