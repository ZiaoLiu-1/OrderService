import requests
import json
import sys

def make_post_request(base_url, service, data):
    url = f"{base_url}/{service.lower()}"  # Construct the URL based on the service
    try:
        headers = {'Content-Type': 'application/json', 'Authorization': 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        print("body:",response.text, "Status code:", response.status_code)
    except Exception as e:
        print(e)

def read_config(config_path):
    with open(config_path, 'r') as file:
        config = json.load(file)
    return config

def make_get_request(base_url, service, data):
    # Construct the URL with the ID directly in the path
    url = f"{base_url}/{service.lower()}/{data['id']}"  # Note the change here to include the ID in the path
    headers = {'Content-Type': 'application/json', 'Authorization': 'Bearer your_token'}
    try:
        response = requests.get(url, headers=headers)
        final_url = response.url  # For debugging, to see the final URL
        print(final_url)
        print(response.text)
    except Exception as e:
        print(e)


def process_command(command_line):
    command_line, _, _ = command_line.partition("#")
    parts = command_line.split()
    service = parts[0]  # The service is the first part of the command (USER, PRODUCT, ORDER)
    if service in ["shutdown", "restart"]:
        command_data = {"command": service}
        service = "order"
        return service, command_data, True
    action = parts[1].lower()  # The action (create, get, update, delete)
    command_data = {"command": action}
    post = True
    if service == "PRODUCT":
        if action == "create":
          if len(parts) != 7:
              return service, command_data, post
          command_data.update({
                "id": parts[2],
                "name": parts[3],
                "description": parts[4],
                "price": parts[5],
                "quantity": parts[6]
            })
          print(command_data)
        elif action =="delete":
          if len(parts) != 7:
              return service, command_data, post
          command_data.update({
            "id": parts[2],
            "name": parts[3],
            "description": parts[4],
            "price": parts[5],
            "quantity": parts[6]
            })
        elif action == "info":
          command_data["id"] = parts[2]
          post = False
        elif action == "update":
          command_data["id"] = parts[2]  # Assuming the ID is always provided
          for part in parts[3:]:
              key, value = part.split(':')
              command_data[key] = value
          for part in parts[3:]:
              key, value = part.split(':')
              command_data[key] = value

    elif service == 'USER':
        if action == "create":
            if len(parts) != 6:
                return service, command_data, post
            command_data.update({
                "id": parts[2],
                "username": parts[3],
                "email": parts[4],
                "password": parts[5]
            })

        elif action =="delete":
          if len(parts) != 6:
                return service, command_data, post
          command_data.update({
            "id": parts[2],
            "username": parts[3],
            "email": parts[4],
            "password": parts[5]
        })
              
        elif action == "get":
            command_data["id"] = parts[2]
            post = False
        elif action == "update":
          command_data["id"] = parts[2]  # Assuming the ID is always provided
          for part in parts[3:]:
              key, value = part.split(':')
              command_data[key] = value
    elif service == "ORDER":
      if action == "place": 
          command_data= ({
              "command" : "place order",
              "product_id": parts[2],
              "quantity": parts[4] if len(parts) == 5 else parts[3]  # Default quantity is 1 if not specified
          })
          # If the user_id is missing, default to user id 1
          if len(parts) == 4:  # Only product_id and quantity are provided
              command_data["user_id"] = "1"  # Default user_id
          elif len(parts) == 5:  # user_id is also provided
              command_data["user_id"] = parts[3]
    print(command_data)
    return service, command_data, post


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 workloadparser.py <filename>")
        sys.exit(1)

    filename = sys.argv[1]

    config = read_config("config.json")
    base_url = f'http://{config["OrderService"]["ip"]}:{config["OrderService"]["port"]}'

    try:
        with open(filename, "r") as file:
            for line in file:
                print(line)
                line = line.strip()
                if not line or line[0] == "#":
                    continue  # Skip empty lines and lines that are now comments
                service, data, post = process_command(line)
                if service is None:
                    continue  # Skip lines that became empty after removing comments
                if post:
                    make_post_request(base_url, service, data)
                else:
                    make_get_request(base_url, service, data)
    except FileNotFoundError:
        print(f"File not found: {filename}")
