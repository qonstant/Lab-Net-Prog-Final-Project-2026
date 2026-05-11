#!/usr/bin/env python3

import base64
import json
import os
import threading
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


REGISTRY_URL = os.environ.get("REGISTRY_URL", "http://localhost:8082/registry/api/v1/registry")
LOCAL_HOST = os.environ.get("LOCAL_HOST", "localhost")
BIND_HOST = os.environ.get("BIND_HOST", "127.0.0.1")
BACKEND_HOST = os.environ.get("BACKEND_HOST")
PORTS = {
    "repo": 9081,
    "aas_registry": 9082,
    "submodel_registry": 9083,
    "discovery": 9084,
}


def http_get_json(url: str) -> Any:
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def paginate(items: list[dict[str, Any]], query: dict[str, list[str]]) -> dict[str, Any]:
    limit = int(query.get("limit", ["100"])[0] or "100")
    cursor = int(query.get("cursor", ["0"])[0] or "0")
    page = items[cursor : cursor + limit]
    payload: dict[str, Any] = {"result": page}
    next_cursor = cursor + limit
    if next_cursor < len(items):
        payload["paging_metadata"] = {"cursor": str(next_cursor)}
    return payload


def normalize_model_type(value: Any) -> Any:
    if isinstance(value, dict) and "name" in value:
        return value["name"]
    return value


def synthetic_submodel_id(aas_id: str, id_short: str) -> str:
    return f"{aas_id}::{id_short}"


def rewrite_backend_url(url: str) -> str:
    if not BACKEND_HOST:
        return url
    parsed = urllib.parse.urlparse(url)
    if parsed.hostname not in {"localhost", "127.0.0.1", "::1"}:
        return url
    if ":" in BACKEND_HOST and not BACKEND_HOST.startswith("["):
        host = f"[{BACKEND_HOST}]"
    else:
        host = BACKEND_HOST
    netloc = host
    if parsed.port is not None:
        netloc = f"{host}:{parsed.port}"
    return urllib.parse.urlunparse(parsed._replace(netloc=netloc))


def decode_base64_identifier(value: str) -> str | None:
    candidates = [value]
    padding = len(value) % 4
    if padding:
        candidates.append(value + ("=" * (4 - padding)))

    for candidate in candidates:
        try:
            decoded = base64.b64decode(candidate, validate=True).decode("utf-8")
        except Exception:  # noqa: BLE001
            try:
                decoded = base64.urlsafe_b64decode(candidate).decode("utf-8")
            except Exception:  # noqa: BLE001
                continue
        if decoded:
            return decoded
    return None


def convert_reference_to_submodel(sm_id: str) -> dict[str, Any]:
    return {
        "type": "ModelReference",
        "keys": [
            {
                "type": "Submodel",
                "value": sm_id,
            }
        ],
    }


def convert_operation_variable(item: dict[str, Any]) -> dict[str, Any]:
    return {"value": convert_submodel_element(item.get("value", {}))}


def convert_submodel_element(item: dict[str, Any]) -> dict[str, Any]:
    model_type = normalize_model_type(item.get("modelType"))
    base: dict[str, Any] = {
        "modelType": model_type,
        "idShort": item.get("idShort"),
    }
    kind = item.get("kind")
    if kind is not None:
        base["kind"] = kind

    if model_type == "Property":
        base["value"] = item.get("value")
        base["valueType"] = item.get("valueType")
    elif model_type == "Operation":
        base["inputVariables"] = [convert_operation_variable(v) for v in item.get("inputVariables", [])]
        base["outputVariables"] = [convert_operation_variable(v) for v in item.get("outputVariables", [])]
        base["inoutputVariables"] = [convert_operation_variable(v) for v in item.get("inoutputVariables", [])]
        if "invokable" in item:
            base["invokable"] = item["invokable"]
        if "isWrappedInvokable" in item:
            base["isWrappedInvokable"] = item["isWrappedInvokable"]
    elif model_type == "SubmodelElementCollection":
        base["value"] = [convert_submodel_element(v) for v in item.get("value", [])]
    elif model_type == "SubmodelElementList":
        base["value"] = [convert_submodel_element(v) for v in item.get("value", [])]
        if "typeValueListElement" in item:
            base["typeValueListElement"] = item["typeValueListElement"]
        if "valueTypeListElement" in item:
            base["valueTypeListElement"] = item["valueTypeListElement"]
    elif model_type == "MultiLanguageProperty":
        base["value"] = item.get("value")
    elif model_type == "File":
        base["value"] = item.get("value")
        base["contentType"] = item.get("mimeType") or item.get("contentType")
    else:
        for field in ("value", "valueType", "semanticId"):
            if field in item:
                base[field] = item[field]
    return {k: v for k, v in base.items() if v is not None}


def parse_element_path(path: str) -> list[tuple[str, Any]]:
    tokens: list[tuple[str, Any]] = []
    buf = []
    i = 0
    while i < len(path):
        ch = path[i]
        if ch == ".":
            if buf:
                tokens.append(("field", "".join(buf)))
                buf = []
            i += 1
            continue
        if ch == "[":
            if buf:
                tokens.append(("field", "".join(buf)))
                buf = []
            end = path.find("]", i)
            if end == -1:
                raise ValueError(f"malformed element path: {path}")
            tokens.append(("index", int(path[i + 1 : end])))
            i = end + 1
            continue
        buf.append(ch)
        i += 1
    if buf:
        tokens.append(("field", "".join(buf)))
    return tokens


def resolve_child(container: dict[str, Any], field: str) -> dict[str, Any]:
    model_type = normalize_model_type(container.get("modelType"))
    if model_type == "Submodel":
        for child in container.get("submodelElements", []):
            if child.get("idShort") == field:
                return child
    elif model_type == "SubmodelElementCollection":
        for child in container.get("value", []):
            if child.get("idShort") == field:
                return child
    elif model_type == "Entity":
        for child in container.get("statements", []):
            if child.get("idShort") == field:
                return child
    raise KeyError(field)


def resolve_index(container: dict[str, Any], index: int) -> dict[str, Any]:
    model_type = normalize_model_type(container.get("modelType"))
    if model_type != "SubmodelElementList":
        raise KeyError(index)
    return container.get("value", [])[index]


def resolve_submodel_element(raw_submodel: dict[str, Any], path: str) -> dict[str, Any]:
    current: dict[str, Any] = raw_submodel
    for kind, value in parse_element_path(path):
        if kind == "field":
            current = resolve_child(current, value)
        else:
            current = resolve_index(current, value)
    return current


@dataclass
class SubmodelInfo:
    id: str
    id_short: str
    source_endpoint: str
    aas_id: str


@dataclass
class AasInfo:
    id: str
    id_short: str
    source_endpoint: str
    submodels: list[SubmodelInfo]


class BackendModel:
    def __init__(self) -> None:
        self.aas_by_id: dict[str, AasInfo] = {}
        self.submodels_by_id: dict[str, SubmodelInfo] = {}

    def refresh(self) -> None:
        registry = http_get_json(REGISTRY_URL)
        aas_by_id: dict[str, AasInfo] = {}
        submodels_by_id: dict[str, SubmodelInfo] = {}

        for aas_desc in registry:
            aas_id = aas_desc["identification"]["id"]
            aas_id_short = aas_desc.get("idShort") or aas_id
            aas_endpoint = rewrite_backend_url(aas_desc["endpoints"][0]["address"])
            submodels: list[SubmodelInfo] = []
            for sm_desc in aas_desc.get("submodels", []):
                sm_id_short = sm_desc.get("idShort") or "Submodel"
                sm_id = synthetic_submodel_id(aas_id, sm_id_short)
                sm_endpoint = rewrite_backend_url(sm_desc["endpoints"][0]["address"])
                info = SubmodelInfo(id=sm_id, id_short=sm_id_short, source_endpoint=sm_endpoint, aas_id=aas_id)
                submodels.append(info)
                submodels_by_id[sm_id] = info
            aas_by_id[aas_id] = AasInfo(id=aas_id, id_short=aas_id_short, source_endpoint=aas_endpoint, submodels=submodels)

        self.aas_by_id = aas_by_id
        self.submodels_by_id = submodels_by_id

    def _ensure_loaded(self) -> None:
        if not self.aas_by_id:
            self.refresh()

    def resolve_aas_id(self, aas_id: str) -> str:
        self._ensure_loaded()
        if aas_id in self.aas_by_id:
            return aas_id
        decoded = decode_base64_identifier(aas_id)
        if decoded and decoded in self.aas_by_id:
            return decoded
        return aas_id

    def resolve_submodel_id(self, sm_id: str) -> str:
        self._ensure_loaded()
        if sm_id in self.submodels_by_id:
            return sm_id
        decoded = decode_base64_identifier(sm_id)
        if decoded and decoded in self.submodels_by_id:
            return decoded
        return sm_id

    def aas_descriptor(self, info: AasInfo) -> dict[str, Any]:
        submodel_descriptors = [self.submodel_descriptor(sm) for sm in info.submodels]
        return {
            "modelType": "AssetAdministrationShellDescriptor",
            "id": info.id,
            "idShort": info.id_short,
            "assetKind": "Instance",
            "globalAssetId": info.id,
            "endpoints": [
                {
                    "interface": "AAS-3.0",
                    "protocolInformation": {
                        "href": f"http://{LOCAL_HOST}:{PORTS['repo']}/shells/{urllib.parse.quote(info.id, safe='')}",
                    },
                }
            ],
            "submodelDescriptors": submodel_descriptors,
            "submodels": submodel_descriptors,
        }

    def submodel_descriptor(self, info: SubmodelInfo) -> dict[str, Any]:
        return {
            "modelType": "SubmodelDescriptor",
            "id": info.id,
            "idShort": info.id_short,
            "endpoints": [
                {
                    "interface": "SUBMODEL-3.0",
                    "protocolInformation": {
                        "href": f"http://{LOCAL_HOST}:{PORTS['repo']}/submodels/{urllib.parse.quote(info.id, safe='')}",
                    },
                }
            ],
        }

    def list_aas_descriptors(self) -> list[dict[str, Any]]:
        self._ensure_loaded()
        return [self.aas_descriptor(info) for info in self.aas_by_id.values()]

    def list_submodel_descriptors(self) -> list[dict[str, Any]]:
        self._ensure_loaded()
        return [self.submodel_descriptor(info) for info in self.submodels_by_id.values()]

    def _fetch_v1_aas(self, info: AasInfo) -> dict[str, Any]:
        return http_get_json(info.source_endpoint)

    def _fetch_v1_submodel(self, info: SubmodelInfo) -> dict[str, Any]:
        return http_get_json(info.source_endpoint)

    def aas(self, aas_id: str) -> dict[str, Any]:
        self._ensure_loaded()
        aas_id = self.resolve_aas_id(aas_id)
        info = self.aas_by_id.get(aas_id)
        if not info:
            return {}
        raw = self._fetch_v1_aas(info)
        asset = raw.get("asset", {})
        asset_identification = asset.get("identification", {})
        return {
            "id": aas_id,
            "idShort": raw.get("idShort") or info.id_short,
            "modelType": "AssetAdministrationShell",
            "assetInformation": {
                "assetKind": asset.get("kind") or "Instance",
                "globalAssetId": asset_identification.get("id") or aas_id,
            },
            "submodels": [convert_reference_to_submodel(sm.id) for sm in info.submodels],
            "submodelDescriptors": [self.submodel_descriptor(sm) for sm in info.submodels],
            "description": [],
            "displayName": [],
            "endpoints": [
                {
                    "interface": "AAS-3.0",
                    "protocolInformation": {
                        "href": f"http://{LOCAL_HOST}:{PORTS['repo']}/shells/{urllib.parse.quote(info.id, safe='')}",
                    },
                }
            ],
        }

    def list_aas(self) -> list[dict[str, Any]]:
        self._ensure_loaded()
        return [self.aas(aas_id) for aas_id in self.aas_by_id]

    def aas_asset_information(self, aas_id: str) -> dict[str, Any]:
        aas = self.aas(aas_id)
        return aas.get("assetInformation", {})

    def aas_submodel_refs(self, aas_id: str) -> list[dict[str, Any]]:
        aas = self.aas(aas_id)
        return aas.get("submodels", [])

    def submodel(self, sm_id: str) -> dict[str, Any]:
        self._ensure_loaded()
        sm_id = self.resolve_submodel_id(sm_id)
        info = self.submodels_by_id.get(sm_id)
        if not info:
            return {}
        raw = self._fetch_v1_submodel(info)
        return {
            "id": sm_id,
            "idShort": raw.get("idShort") or info.id_short,
            "modelType": "Submodel",
            "kind": raw.get("kind") or "Instance",
            "description": [],
            "displayName": [],
            "submodelElements": [convert_submodel_element(v) for v in raw.get("submodelElements", [])],
        }

    def submodel_elements(self, sm_id: str) -> list[dict[str, Any]]:
        return self.submodel(sm_id).get("submodelElements", [])

    def submodel_element(self, sm_id: str, element_path: str) -> dict[str, Any]:
        self._ensure_loaded()
        sm_id = self.resolve_submodel_id(sm_id)
        info = self.submodels_by_id.get(sm_id)
        if not info:
            return {}
        raw = self._fetch_v1_submodel(info)
        element = resolve_submodel_element(raw, element_path)
        return convert_submodel_element(element)

    def list_submodels(self) -> list[dict[str, Any]]:
        self._ensure_loaded()
        return [self.submodel(sm_id) for sm_id in self.submodels_by_id]

    def lookup_shell_ids(self, global_asset_id: str) -> list[str]:
        self._ensure_loaded()
        matches = []
        for aas_id in self.aas_by_id:
            asset_info = self.aas_asset_information(aas_id)
            if asset_info.get("globalAssetId") == global_asset_id:
                matches.append(aas_id)
        return matches


MODEL = BackendModel()


class CompatHandler(BaseHTTPRequestHandler):
    server_version = "BaSyxV2Compat/1.0"

    def _send_json(self, status: int, payload: Any) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self) -> None:
        self._send_json(200, {})

    def do_GET(self) -> None:
        try:
            self._handle_get()
        except urllib.error.HTTPError as exc:
            self._send_json(exc.code, {"error": str(exc)})
        except Exception as exc:  # noqa: BLE001
            self._send_json(500, {"error": str(exc)})

    def _handle_get(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)
        port = self.server.server_port

        if path == "/description":
            self._send_json(200, {"service": "basyx-v2-compat", "port": port})
            return

        if port == PORTS["repo"]:
            self._handle_repo(path, query)
            return
        if port == PORTS["aas_registry"]:
            self._handle_aas_registry(path, query)
            return
        if port == PORTS["submodel_registry"]:
            self._handle_submodel_registry(path, query)
            return
        if port == PORTS["discovery"]:
            self._handle_discovery(path, query)
            return

        self._send_json(404, {"error": "unknown port"})

    def _handle_repo(self, path: str, query: dict[str, list[str]]) -> None:
        if path == "/shells":
            self._send_json(200, paginate(MODEL.list_aas(), query))
            return
        if path == "/submodels":
            self._send_json(200, paginate(MODEL.list_submodels(), query))
            return
        if path == "/concept-descriptions":
            self._send_json(200, {"result": []})
            return
        if path.startswith("/shells/"):
            tail = path[len("/shells/") :]
            aas_id, _, remainder = tail.partition("/")
            aas_id = urllib.parse.unquote(aas_id)
            if remainder == "":
                payload = MODEL.aas(aas_id)
                self._send_json(200, payload if payload else {})
                return
            if remainder == "asset-information":
                self._send_json(200, MODEL.aas_asset_information(aas_id))
                return
            if remainder == "submodel-refs":
                self._send_json(200, {"result": MODEL.aas_submodel_refs(aas_id)})
                return
        if path.startswith("/submodels/"):
            tail = path[len("/submodels/") :]
            if tail.endswith("/submodel-elements"):
                sm_id = MODEL.resolve_submodel_id(urllib.parse.unquote(tail[: -len("/submodel-elements")]))
                self._send_json(200, MODEL.submodel_elements(sm_id))
                return
            if tail.endswith("/submodel-elements/"):
                sm_id = MODEL.resolve_submodel_id(urllib.parse.unquote(tail[: -len("/submodel-elements/")]))
                self._send_json(200, MODEL.submodel_elements(sm_id))
                return
            sm_id, marker, remainder = tail.partition("/submodel-elements/")
            sm_id = urllib.parse.unquote(sm_id)
            if marker:
                element_path = urllib.parse.unquote(remainder)
                payload = MODEL.submodel_element(sm_id, element_path)
                self._send_json(200, payload if payload else {})
                return
            self._send_json(200, MODEL.submodel(sm_id))
            return
        self._send_json(404, {"error": "not found"})

    def _handle_aas_registry(self, path: str, query: dict[str, list[str]]) -> None:
        descriptors = MODEL.list_aas_descriptors()
        if path == "/shell-descriptors":
            self._send_json(200, paginate(descriptors, query))
            return
        if path.startswith("/shell-descriptors/"):
            aas_id = MODEL.resolve_aas_id(urllib.parse.unquote(path[len("/shell-descriptors/") :]))
            for desc in descriptors:
                if desc["id"] == aas_id:
                    self._send_json(200, desc)
                    return
            self._send_json(404, {})
            return
        self._send_json(404, {"error": "not found"})

    def _handle_submodel_registry(self, path: str, query: dict[str, list[str]]) -> None:
        descriptors = MODEL.list_submodel_descriptors()
        if path == "/submodel-descriptors":
            self._send_json(200, paginate(descriptors, query))
            return
        if path.startswith("/submodel-descriptors/"):
            sm_id = MODEL.resolve_submodel_id(urllib.parse.unquote(path[len("/submodel-descriptors/") :]))
            for desc in descriptors:
                if desc["id"] == sm_id:
                    self._send_json(200, desc)
                    return
            self._send_json(404, {})
            return
        self._send_json(404, {"error": "not found"})

    def _handle_discovery(self, path: str, query: dict[str, list[str]]) -> None:
        if path != "/lookup/shells":
            self._send_json(404, {"error": "not found"})
            return
        asset_ids = query.get("assetIds", [])
        if not asset_ids:
            self._send_json(200, {"result": []})
            return
        try:
            request = json.loads(urllib.parse.unquote(asset_ids[0]))
            matches = MODEL.lookup_shell_ids(request.get("value", ""))
        except json.JSONDecodeError:
            matches = []
        self._send_json(200, {"result": matches})


def serve(port: int) -> None:
    server = ThreadingHTTPServer((BIND_HOST, port), CompatHandler)
    server.serve_forever()


def main() -> None:
    threads = []
    for port in PORTS.values():
        thread = threading.Thread(target=serve, args=(port,), daemon=True)
        thread.start()
        threads.append(thread)
    for thread in threads:
        thread.join()


if __name__ == "__main__":
    main()
