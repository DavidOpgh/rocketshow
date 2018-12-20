import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { ActivityDmx } from '../models/activity-dmx';
import { $WebSocket, WebSocketConfig } from 'angular2-websocket';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ActivityDmxService {

  public subject: Subject<ActivityDmx> = new Subject();

  // The websocket endpoint url
  private wsUrl: string;

  // The websocket connection
  websocket: $WebSocket;

  monitors: number = 0;

  constructor(private http: HttpClient
  ) {
    // Create the backend-url
    if (environment.name == 'dev') {
      this.wsUrl = 'ws://' + environment.localBackend + '/';
    } else {
      this.wsUrl = 'ws://' + window.location.hostname + ':' + window.location.port + '/';
    }

    this.wsUrl += 'api/activity/dmx';
  }

  startMonitor() {
    this.monitors ++;

    if(!this.websocket) {
    // Connect to the websocket backend
    const wsConfig = { reconnectIfNotNormalClose: true } as WebSocketConfig;
    this.websocket = new $WebSocket(this.wsUrl, null, wsConfig);

    this.websocket.onMessage(
      (msg: MessageEvent) => {
        this.subject.next(new ActivityDmx(JSON.parse(msg.data)));
      },
      { autoApply: false }
    );
    }
  }

  stopMonitor() {
    this.monitors --;

    if(this.monitors < 1 && this.websocket) {
      this.websocket.close();
      this.websocket = undefined;
    }
  }

}
