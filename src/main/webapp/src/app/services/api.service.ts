import { StateService } from './state.service';
import { Http, XHRBackend, RequestOptions, Request, RequestOptionsArgs, Response, Headers, ResponseContentType } from '@angular/http';

import { State } from './../models/state';
import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs/Rx';
import { environment } from '../../environments/environment';

@Injectable()
export class ApiService extends Http {

  // The rest endpoint base url
  private restUrl: string;

  constructor(
    backend: XHRBackend,
    options: RequestOptions,
    private http: Http
  ) {
    super(backend, options);

    // Create the backend-url
    if (environment.name == 'dev') {
      this.restUrl = 'http://' + environment.localBackend + '/';
    } else {
      this.restUrl = '/'
    }

    this.restUrl += 'api/';
  }

  getRestUrl(): string {
    return this.restUrl;
  }

  get(url: string, options: RequestOptionsArgs = undefined): Observable<Response> {
    return super.get(this.restUrl + url, options);
  }

  post(url: string, body: any): Observable<Response> {
    let headers = new Headers();
    headers.append('Content-Type', 'application/json');
    return super.post(this.restUrl + url, body, {headers: headers});
  }

  put(url: string, body: any): Observable<Response> {
    return super.put(this.restUrl + url, body);
  }

  delete(url: string): Observable<Response> {
    return super.delete(this.restUrl + url);
  }

}
